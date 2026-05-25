package com.mindlink.recommendation.service;

import com.mindlink.recommendation.EmotionCategory;
import com.mindlink.recommendation.client.GeminiClient;
import com.mindlink.recommendation.client.NaverBookApiClient;
import com.mindlink.recommendation.domain.RecommendationBook;
import com.mindlink.recommendation.dto.BookDto;
import com.mindlink.recommendation.dto.EmotionSlot;
import com.mindlink.recommendation.dto.GeminiAnalysisResult;
import com.mindlink.recommendation.dto.GeminiSelectionResult;
import com.mindlink.recommendation.dto.NaverBookItem;
import com.mindlink.recommendation.dto.RecommendationResponse;
import com.mindlink.recommendation.repository.RecommendationBookRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 도서 추천 핵심 로직.
 *  일반 GET: DB → (없으면) EMPTY
 *  AI POST: ①Gemini 판단 → ②DB·네이버 검색으로 후보 수집 → ③Gemini 선별
 */
@Service
public class RecommendationService {

    private final RecommendationBookRepository repo;
    private final NaverBookApiClient           naverClient;
    private final GeminiClient                 geminiClient;

    public RecommendationService(RecommendationBookRepository repo,
                          NaverBookApiClient naverClient,
                          GeminiClient geminiClient) {
        this.repo         = repo;
        this.naverClient  = naverClient;
        this.geminiClient = geminiClient;
    }

    /**
     * TODO [설문 모듈 연동 필요]
     *  현재: 프론트가 emotion 쿼리 파라미터를 문자열로 전달
     *  예) GET /api/recommendations?emotion=DEPRESSION&size=5
     *  설문 모듈의 결과값 형식이 확정되면 parseEmotion() 수정 필요
     */
    public RecommendationResponse getRecommendations(String emotionStr, int size) {
        EmotionCategory emotion = parseEmotion(emotionStr);

        // ── 1단계: 자체 DB 조회 ────────────────────────────────────
        List<RecommendationBook> dbBooks = repo.findByEmotion(emotion);

        if (!dbBooks.isEmpty()) {
            List<BookDto> books = dbBooks.stream()
                    .limit(size)
                    .map(b -> new BookDto(b.getTitle(), b.getAuthor(), b.getPublisher(),
                            b.getLink(), b.getImage(), b.getDescription(),
                            b.getIsbn() != null ? b.getIsbn() : "",
                            b.getEmotion().name()))
                    .toList();
            // Gemini 위로 멘트 → 없으면 기본값
            String reason = geminiClient.generateComfortMessage(emotion.name())
                    .orElse(emotion.getReason());
            return new RecommendationResponse(emotion.name(), reason, books, "DB");
        }

        // ── DB 0건 → 빈 목록 반환 (실시간 네이버 검색 제거) ──────────
        String reason = geminiClient.generateComfortMessage(emotion.name())
                .orElse(emotion.getReason());
        return new RecommendationResponse(emotion.name(), reason, List.of(), "EMPTY");
    }

    /** 네이버 신규 도서를 비동기로 Gemini 분류 후 DB INSERT */
    private void asyncCache(NaverBookItem item, String isbn,
                            EmotionCategory fallback, String searchKeyword) {
        CompletableFuture.runAsync(() -> {
            try {
                if (repo.findByIsbn(isbn).isPresent()) return; // race-condition 재확인
                EmotionCategory tag = geminiClient
                        .extractEmotionTag(item.getTitle(), item.getDescription())
                        .orElse(fallback);
                RecommendationBook book = new RecommendationBook();
                book.setEmotion(tag);
                book.setTitle(strip(item.getTitle()));
                book.setAuthor(item.getAuthor());
                book.setPublisher(item.getPublisher());
                book.setLink(item.getLink());
                book.setImage(item.getImage());
                book.setDescription(strip(item.getDescription()));
                book.setIsbn(isbn);
                book.setSearchKeyword(searchKeyword);
                repo.save(book);
            } catch (Exception ignored) {} // unique 제약 위반 등 무시
        });
    }

    private static final int AI_NAVER_DISPLAY = 14;
    private static final int MAX_DB_CANDIDATES_AI = 14;

    /** ① Gemini로 사용자 상태 판단 → (보조) Gemini가 NORMAL일 때만 키워드로 감정 보정 */
    private AiStage1State aiStage1JudgeUserState(String userMessage) {
        GeminiAnalysisResult analysis = geminiClient.analyzeUserMessage(userMessage).orElse(null);
        EmotionCategory gemEmotion = analysis != null ? parseEmotion(analysis.emotion) : EmotionCategory.NORMAL;
        EmotionCategory emotion = resolveEmotionForAi(userMessage, analysis, gemEmotion);
        String userSummary = analysis != null && analysis.summary != null ? analysis.summary.trim() : "";
        return new AiStage1State(analysis, emotion, userSummary);
    }

    /**
     * ② ①단계에서 확정한 감정·검색어를 기준으로 DB·네이버 검색 API만 호출해 후보 목록을 만든다.
     * (이 단계에서는 Gemini를 호출하지 않는다.)
     * 최근 AI 추천 ISBN(excludeRecentIsbns)은 후보에서 제외해 같은 책 반복을 줄인다.
     * 네이버는 정렬·시작점·보조 검색어를 바꿔 두 번 묶어 후보 다양화.
     */
    private AiStage2Retrieval aiStage2FetchCandidates(AiStage1State stage1, String userMessage,
                                                      List<String> excludeRecentIsbns) {
        EmotionCategory emotion = stage1.emotion();
        GeminiAnalysisResult analysis = stage1.analysis();

        Set<String> exclude = new LinkedHashSet<>();
        if (excludeRecentIsbns != null) {
            for (String x : excludeRecentIsbns) {
                if (x != null && !x.isBlank()) {
                    exclude.add(x.trim());
                }
            }
        }

        int divSeed = Objects.hash(
                userMessage == null ? "" : userMessage,
                stage1.userSummary(),
                stage1.emotion().name(),
                System.currentTimeMillis() / 120_000L);

        List<RecommendationBook> dbBooks = repo.findByEmotion(emotion);
        List<RecommendationBook> dbSlice = limitAndShuffleDbBooks(dbBooks, MAX_DB_CANDIDATES_AI, divSeed);

        List<BookDto> candidates = new ArrayList<>();
        for (RecommendationBook b : dbSlice) {
            String isbn = b.getIsbn() != null ? b.getIsbn() : "";
            if (!isbn.isEmpty() && exclude.contains(isbn)) {
                continue;
            }
            candidates.add(new BookDto(b.getTitle(), b.getAuthor(), b.getPublisher(),
                    b.getLink(), b.getImage(), b.getDescription(),
                    isbn, b.getEmotion().name()));
        }

        String naverQuery = buildAiNaverQuery(emotion, analysis, userMessage);
        String sort1 = pickNaverSort(divSeed);
        String sort2 = pickNaverSort(divSeed + 19);
        int start1 = pickNaverStart(divSeed);
        int start2 = pickNaverStart(divSeed + 31);
        String query2 = secondNaverAngle(naverQuery, emotion, divSeed);

        List<NaverBookItem> round1 = naverClient.search(naverQuery, AI_NAVER_DISPLAY, start1, sort1);
        List<NaverBookItem> round2 = naverClient.search(query2, Math.max(6, AI_NAVER_DISPLAY - 4), start2, sort2);

        mergeNaverCandidates(candidates, round1, exclude);
        mergeNaverCandidates(candidates, round2, exclude);

        String source;
        if (dbBooks.isEmpty()) {
            source = candidates.isEmpty() ? "EMPTY" : "NAVER";
        } else if (round1.isEmpty() && round2.isEmpty()) {
            source = "DB";
        } else {
            source = "DB+NAVER";
        }
        return new AiStage2Retrieval(candidates, source, naverQuery);
    }

    private static List<RecommendationBook> limitAndShuffleDbBooks(List<RecommendationBook> all, int max, int seed) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        if (all.size() <= max) {
            return new ArrayList<>(all);
        }
        List<RecommendationBook> copy = new ArrayList<>(all);
        Collections.shuffle(copy, new Random(seed));
        return new ArrayList<>(copy.subList(0, max));
    }

    private static String pickNaverSort(int seed) {
        return switch (Math.floorMod(seed, 3)) {
            case 1 -> "date";
            case 2 -> "count";
            default -> "sim";
        };
    }

    private static int pickNaverStart(int seed) {
        return 1 + Math.floorMod(seed >> 3, 7) * 4;
    }

    private static String secondNaverAngle(String base, EmotionCategory emotion, int seed) {
        String[] extras = {
                " 자기돌봄 실천",
                " 에세이 공감",
                " 마음챙김 일상",
                " 관계 대화",
                " 휴식 루틴",
                " 감정 이해"
        };
        String x = extras[Math.floorMod(seed, extras.length)];
        String b = base == null ? "" : base.trim();
        if (b.isBlank()) {
            String a = emotion.getSearchQuery().trim() + x;
            return a.length() > 130 ? a.substring(0, 130) : a;
        }
        if (b.length() + x.length() > 115) {
            int keep = Math.max(25, 110 - x.length());
            return b.substring(0, Math.min(keep, b.length())) + x;
        }
        return b + x;
    }

    /** ③ Gemini 선별 + 서버 관련도 검증·보완(점수·차단)으로 최종 3권 확정 */
    private RecommendationResponse aiStage3SelectFromCandidates(
            AiStage1State stage1, AiStage2Retrieval stage2, String userMessage) {
        EmotionCategory emotion = stage1.emotion();
        List<BookDto> candidates = stage2.candidates();
        String source = stage2.source();
        String summary = stage1.userSummary();

        GeminiSelectionResult selection = geminiClient
                .selectBooksWithReason(userMessage, summary, emotion.name(), candidates)
                .orElse(null);

        List<Integer> rankedIdx = rankedCandidateIndices(userMessage, summary, emotion, candidates);
        LinkedHashSet<Integer> chosen = new LinkedHashSet<>();

        int geminiAccepted = 0;
        if (selection != null) {
            for (int idx : selection.selectedIndices) {
                if (idx < 0 || idx >= candidates.size()) continue;
                if (relevanceScore(userMessage, summary, emotion, candidates.get(idx)) < MIN_SCORE_TO_ACCEPT_GEMINI_PICK) {
                    continue;
                }
                chosen.add(idx);
                geminiAccepted++;
                if (chosen.size() >= 3) break;
            }
        }
        for (int idx : rankedIdx) {
            if (chosen.size() >= 3) break;
            chosen.add(idx);
        }

        Map<Integer, String> tagByIndex = (selection != null && selection.bookEmotionsByIndex != null)
                ? selection.bookEmotionsByIndex
                : Map.of();
        List<BookDto> finalBooks = new ArrayList<>();
        for (int idx : chosen) {
            if (finalBooks.size() >= 3) break;
            BookDto o = candidates.get(idx);
            finalBooks.add(copyBookWithDisplayEmotion(o, tagByIndex.get(idx), emotion));
        }

        String naverQueryUsed = stage2.naverQueryUsed();
        if (naverQueryUsed != null && naverQueryUsed.length() > 500) {
            naverQueryUsed = naverQueryUsed.substring(0, 500);
        }
        for (BookDto b : finalBooks) {
            persistBookFromAi(b, parseEmotion(b.getBookEmotion()), naverQueryUsed != null ? naverQueryUsed : "");
        }

        String reason;
        if (geminiAccepted > 0 && selection != null && !selection.reason.isBlank()) {
            reason = selection.reason;
        } else {
            reason = geminiClient.generateComfortMessage(emotion.name(), userMessage)
                    .orElse(!finalBooks.isEmpty()
                            ? "말씀해 주신 내용과 연관된 키워드를 바탕으로 검색된 도서 중에서 골라 보았어요."
                            : emotion.getReason());
        }

        String lower = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        if (isSelfHarmCrisisMessage(lower)) {
            reason = CRISIS_SUPPORT_MESSAGE_PREFIX + reason;
        }

        return new RecommendationResponse(emotion.name(), reason, finalBooks, source);
    }

    /** Gemini가 고른 인덱스는 이 점수 이상일 때만 채택 (메시지·요약·감정 키워드와 무관한 권수 배제) */
    private static final int MIN_SCORE_TO_ACCEPT_GEMINI_PICK = 1;

    private static final List<String> TITLE_BLOCK_SUBSTRINGS = List.of(
            "수능", "워크북", "문제집", "취급설명서", "omr", "만화", "웹툰",
            "national geographic", "키즈 스콜라", "eb수능",
            "내셔널지오그래픽", "지오그래픽", "national geographic kids",
            "이상하고 재미있는", "잡학사전",
            "마이클 샌델", "michael sandel", "what's the right thing", "what is the right thing",
            "대한민국이 읽은", "정의란");

    private static boolean isPoorFitForMindLink(BookDto b) {
        String t = (b.getTitle() + " " + b.getPublisher() + " " + b.getDescription()).toLowerCase(Locale.ROOT);
        for (String bad : TITLE_BLOCK_SUBSTRINGS) {
            if (t.contains(bad.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static int emotionCorpusBonus(EmotionCategory e, String corpus) {
        return switch (e) {
            case DEPRESSION -> (corpus.contains("위로") || corpus.contains("에세이") || corpus.contains("우울") || corpus.contains("힐링")) ? 4 : 0;
            case STRESS -> (corpus.contains("스트레스") || corpus.contains("명상") || corpus.contains("마음챙김")
                    || corpus.contains("휴식") || corpus.contains("번아웃") || corpus.contains("마인드")) ? 4 : 0;
            case ANXIETY -> (corpus.contains("불안") || corpus.contains("마음챙김") || corpus.contains("공황") || corpus.contains("긴장")) ? 4 : 0;
            case LETHARGY -> (corpus.contains("동기") || corpus.contains("습관") || corpus.contains("자기계발") || corpus.contains("루틴")) ? 4 : 0;
            case RELATIONSHIP -> (corpus.contains("관계") || corpus.contains("소통") || corpus.contains("대화")
                    || corpus.contains("애착") || corpus.contains("경계") || corpus.contains("연애")) ? 4 : 0;
            case NORMAL -> 0;
        };
    }

    private static List<String> splitSearchTokens(String text) {
        if (text == null || text.isBlank()) return List.of();
        String n = text.toLowerCase(Locale.ROOT).replaceAll("[\\n\\r\\t]+", " ");
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String p : n.split("[\\s,.!?;:'\"\\[\\]()\\-·/]+")) {
            if (p.length() >= 2) set.add(p);
            if (p.length() >= 3) set.add(p.substring(0, 2));
        }
        String trim = n.trim();
        if (trim.length() >= 2 && trim.length() <= 50) set.add(trim);
        return new ArrayList<>(set);
    }

    private static boolean userSoundsEmotionallyDistressed(String lowerUser) {
        return containsAny(lowerUser,
                "힘들", "괴로", "지쳐", "슬프", "우울", "외로", "불안", "스트레스", "답답", "막막",
                "허탈", "절망", "stress", "depression", "anxiety");
    }

    /** 제목이 한글 위주 서적이 아니라 영미 베스트셀러 교양처럼 보이면 true */
    private static boolean titleLooksMostlyLatinLetters(String title) {
        if (title == null || title.isBlank()) return false;
        long letters = title.chars().filter(Character::isLetter).count();
        if (letters < 6) return false;
        long latin = title.chars().filter(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')).count();
        return latin * 2 >= letters;
    }

    private static int relevanceScore(String userMessage, String userSummary, EmotionCategory emotion, BookDto b) {
        if (isPoorFitForMindLink(b)) return -9999;
        if (isMentalHealthRecommendationNoise(b, userMessage, emotion)) return -9999;
        String u = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        String corpus = (b.getTitle() + " " + b.getDescription()).toLowerCase(Locale.ROOT);
        int score = emotionCorpusBonus(emotion, corpus);
        for (String tok : splitSearchTokens(userMessage)) {
            if (corpus.contains(tok)) score += 3;
        }
        for (String tok : splitSearchTokens(userSummary)) {
            if (corpus.contains(tok)) score += 2;
        }
        if (userSoundsEmotionallyDistressed(u)) {
            if (titleLooksMostlyLatinLetters(b.getTitle())) score -= 25;
            if (corpus.contains("위로") || corpus.contains("에세이") || corpus.contains("힐링")
                    || corpus.contains("마음을") || corpus.contains("마음의") || corpus.contains("공감")) {
                score += 10;
            }
        }
        return score;
    }

    private static List<Integer> rankedCandidateIndices(String userMessage, String userSummary,
                                                        EmotionCategory emotion, List<BookDto> candidates) {
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            int s = relevanceScore(userMessage, userSummary, emotion, candidates.get(i));
            if (s <= -1000) continue;
            s += ThreadLocalRandom.current().nextInt(0, 4);
            s += (Objects.hash(i, userMessage, userSummary, emotion) & 0x3);
            pairs.add(new int[]{i, s});
        }
        pairs.sort((a, b) -> {
            int c = Integer.compare(b[1], a[1]);
            return c != 0 ? c : Integer.compare(a[0], b[0]);
        });
        return pairs.stream().map(a -> a[0]).toList();
    }

    /**
     * POST /api/recommendations/ai
     * ① Gemini: 입력 → 상태(감정·검색어·요약) 판단
     * ② 서버: 상태 기준으로 DB·네이버 API로 후보만 수집
     * ③ Gemini: 후보목록만 보고 3권·이유 확정
     */
    public RecommendationResponse getAiRecommendations(String userMessage, List<String> excludeRecentIsbns) {
        AiStage1State stage1 = aiStage1JudgeUserState(userMessage);
        List<String> ex = excludeRecentIsbns != null ? excludeRecentIsbns : List.of();
        AiStage2Retrieval stage2 = aiStage2FetchCandidates(stage1, userMessage, ex);

        if (stage2.candidates().isEmpty()) {
            EmotionCategory emotion = stage1.emotion();
            String reason = geminiClient.generateComfortMessage(emotion.name(), userMessage)
                    .orElse(emotion.getReason());
            return new RecommendationResponse(emotion.name(), reason, List.of(), "EMPTY");
        }
        return aiStage3SelectFromCandidates(stage1, stage2, userMessage);
    }

    private record AiStage1State(GeminiAnalysisResult analysis, EmotionCategory emotion, String userSummary) {}

    private record AiStage2Retrieval(List<BookDto> candidates, String source, String naverQueryUsed) {}

    /**
     * POST /api/recommendations/personalize
     * 복합 감정 슬롯 기반 DB 추천 — 네이버 API 호출 없음.
     * detected(N) → slots(Gemini or fallback) → 감정별 DB 후보 → 슬롯 쿼터 선택
     */
    public RecommendationResponse getPersonalizedRecommendations(String message, String emotionParam,
                                                          List<String> excludeRecentIsbns) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);

        // 1. 복수 감정 탐지
        List<EmotionCategory> detected = new ArrayList<>(detectEmotionsFromKeywords(lower));
        if (emotionParam != null && !emotionParam.isBlank()) {
            EmotionCategory explicit = parseEmotion(emotionParam);
            if (explicit != EmotionCategory.NORMAL && !detected.contains(explicit)) {
                detected.add(0, explicit);
                if (detected.size() > 2) detected = detected.subList(0, 2);
            }
        }

        // 2. 슬롯 결정 (Gemini or fallback, 합=3)
        boolean usedGeminiForSlots = false;
        List<EmotionSlot> slots;
        Optional<List<EmotionSlot>> geminiSlots = geminiClient.resolveRecommendationSlots(message, detected);
        if (geminiSlots.isPresent()) {
            slots = geminiSlots.get();
            usedGeminiForSlots = true;
        } else {
            slots = fallbackSlots(detected);
        }
        EmotionCategory primaryEmotion = slots.isEmpty() ? EmotionCategory.NORMAL : slots.get(0).emotion();

        // 3. 슬롯별 DB 후보 수집
        Set<String> exclude = buildExcludeSet(excludeRecentIsbns);
        Map<EmotionCategory, List<BookDto>> candidatesByEmotion = buildDbCandidatesForSlots(slots, exclude, message);

        // 4. 슬롯 쿼터 적용해 3권 선택
        List<BookDto> finalBooks = pickBooksWithSlotQuota(slots, candidatesByEmotion, message, primaryEmotion);

        if (finalBooks.isEmpty()) {
            String reason = geminiClient.generateComfortMessage(primaryEmotion.name(), message)
                    .orElse(primaryEmotion.getReason());
            List<String> emotionNames = slots.stream().map(s -> s.emotion().name())
                    .distinct().toList();
            return new RecommendationResponse(primaryEmotion.name(), reason, List.of(), "EMPTY", true, emotionNames);
        }

        // 5. 위로 멘트
        String reason;
        if (detected.isEmpty()) {
            reason = geminiClient.generateComfortMessage(primaryEmotion.name(), message)
                    .orElse("말씀해 주신 내용을 바탕으로, 마음이음이 선별해 둔 다양한 도서를 추천해 드려요.");
        } else {
            reason = geminiClient.generateComfortMessage(primaryEmotion.name(), message)
                    .orElse(slots.size() >= 2 ? buildMultiEmotionReason(slots) : primaryEmotion.getReason());
        }
        if (isSelfHarmCrisisMessage(lower)) {
            reason = CRISIS_SUPPORT_MESSAGE_PREFIX + reason;
        }

        List<String> emotionNames = slots.stream().map(s -> s.emotion().name()).distinct().toList();
        String source = usedGeminiForSlots ? "DB+AI" : "DB";
        return new RecommendationResponse(primaryEmotion.name(), reason, finalBooks, source, true, emotionNames);
    }

    /** 복수 감정 탐지 — 독립 스캔, 최대 2개 반환 */
    private List<EmotionCategory> detectEmotionsFromKeywords(String lower) {
        if (lower == null || lower.isBlank()) return List.of();
        List<EmotionCategory> out = new ArrayList<>();
        if (containsAny(lower, "우울", "depression", "절망", "허탈", "허무",
                "힘들", "괴로", "지쳐", "슬프", "슬퍼", "막막")) out.add(EmotionCategory.DEPRESSION);
        if (containsAny(lower, "스트레스", "stress")) out.add(EmotionCategory.STRESS);
        if (containsAny(lower, "불안", "anxiety", "불안장애", "초조", "불면", "답답", "긴장", "공황")) out.add(EmotionCategory.ANXIETY);
        if (containsAny(lower, "무기력", "lethargy", "번아웃", "burnout", "의욕", "나태")) out.add(EmotionCategory.LETHARGY);
        if (containsAny(lower, "관계", "친구", "가족", "연인", "relationship", "소통", "외로", "이별", "갈등")) out.add(EmotionCategory.RELATIONSHIP);
        return out.size() > 2 ? new ArrayList<>(out.subList(0, 2)) : out;
    }

    /** 키워드 미매칭 시 DB 전체에서 감정 다양하게 3권 (1+1+1) */
    private static final List<EmotionCategory> DIVERSE_DEFAULT_EMOTIONS = List.of(
            EmotionCategory.STRESS, EmotionCategory.ANXIETY, EmotionCategory.DEPRESSION);

    /** Gemini 비활성 시 슬롯 fallback */
    private List<EmotionSlot> fallbackSlots(List<EmotionCategory> detected) {
        if (detected.size() >= 2)
            return List.of(new EmotionSlot(detected.get(0), 2), new EmotionSlot(detected.get(1), 1));
        if (detected.size() == 1)
            return List.of(new EmotionSlot(detected.get(0), 3));
        return List.of(
                new EmotionSlot(DIVERSE_DEFAULT_EMOTIONS.get(0), 1),
                new EmotionSlot(DIVERSE_DEFAULT_EMOTIONS.get(1), 1),
                new EmotionSlot(DIVERSE_DEFAULT_EMOTIONS.get(2), 1));
    }

    private Set<String> buildExcludeSet(List<String> excludeIsbns) {
        Set<String> exclude = new LinkedHashSet<>();
        if (excludeIsbns != null) {
            for (String x : excludeIsbns) {
                if (x != null && !x.isBlank()) exclude.add(x.trim());
            }
        }
        return exclude;
    }

    /** 슬롯별 DB 후보 수집 — 감정별로 분리, 제목 중복 제거 */
    private Map<EmotionCategory, List<BookDto>> buildDbCandidatesForSlots(
            List<EmotionSlot> slots, Set<String> excludeIsbns, String message) {
        Map<EmotionCategory, List<BookDto>> result = new LinkedHashMap<>();
        Set<String> seenTitles = new HashSet<>();
        int divSeed = Objects.hash(message == null ? "" : message, System.currentTimeMillis() / 120_000L);

        for (EmotionSlot slot : slots) {
            List<RecommendationBook> dbBooks = new ArrayList<>(repo.findByEmotion(slot.emotion()));
            if (dbBooks.size() < slot.count()) {
                // 같은 감정 통이 부족하면 전체에서 보충 (emotion 태그 동일 행만)
                Set<Long> knownIds = dbBooks.stream().map(RecommendationBook::getId)
                        .collect(java.util.stream.Collectors.toSet());
                for (RecommendationBook b : repo.findAll()) {
                    if (b.getEmotion() == slot.emotion() && !knownIds.contains(b.getId())) {
                        dbBooks.add(b);
                        knownIds.add(b.getId());
                        if (dbBooks.size() >= 20) break;
                    }
                }
            }
            List<RecommendationBook> sliced = limitAndShuffleDbBooks(
                    dbBooks, MAX_DB_CANDIDATES_AI, divSeed + slot.emotion().ordinal());
            List<BookDto> candidates = new ArrayList<>();
            for (RecommendationBook b : sliced) {
                String isbn = b.getIsbn() != null ? b.getIsbn() : "";
                if (!isbn.isEmpty() && excludeIsbns.contains(isbn)) continue;
                String titleKey = normalizeTitleKey(b.getTitle());
                if (seenTitles.contains(titleKey)) continue;
                seenTitles.add(titleKey);
                candidates.add(new BookDto(b.getTitle(), b.getAuthor(), b.getPublisher(),
                        b.getLink(), b.getImage(), b.getDescription(), isbn, b.getEmotion().name()));
            }
            if (candidates.isEmpty()) {
                candidates = loadDiverseBookDtos(excludeIsbns, seenTitles, divSeed + slot.emotion().ordinal(), MAX_DB_CANDIDATES_AI);
            }
            result.put(slot.emotion(), candidates);
        }
        return result;
    }

    /** 감정 키워드 없을 때·통이 비었을 때 — DB 전체에서 후보 (권별 bookEmotion은 행 그대로) */
    private List<BookDto> loadDiverseBookDtos(Set<String> excludeIsbns, Set<String> seenTitles, int divSeed, int max) {
        List<RecommendationBook> sliced = limitAndShuffleDbBooks(new ArrayList<>(repo.findAll()), max, divSeed);
        List<BookDto> out = new ArrayList<>();
        for (RecommendationBook b : sliced) {
            String isbn = b.getIsbn() != null ? b.getIsbn() : "";
            if (!isbn.isEmpty() && excludeIsbns.contains(isbn)) continue;
            String titleKey = normalizeTitleKey(b.getTitle());
            if (seenTitles.contains(titleKey)) continue;
            seenTitles.add(titleKey);
            out.add(new BookDto(b.getTitle(), b.getAuthor(), b.getPublisher(),
                    b.getLink(), b.getImage(), b.getDescription(), isbn, b.getEmotion().name()));
        }
        return out;
    }

    /** 슬롯 쿼터 강제 선택 — 각 감정 통에서 relevance 상위로 slot.count()권씩, 미달 시 다른 통으로 보충 */
    private List<BookDto> pickBooksWithSlotQuota(
            List<EmotionSlot> slots, Map<EmotionCategory, List<BookDto>> candidatesByEmotion,
            String message, EmotionCategory primaryEmotion) {
        List<BookDto> result = new ArrayList<>();
        Set<String> chosenTitles = new HashSet<>();

        // 패스 1: 슬롯 쿼터대로 선택
        for (EmotionSlot slot : slots) {
            if (result.size() >= 3) break;
            List<BookDto> pool = candidatesByEmotion.getOrDefault(slot.emotion(), List.of())
                    .stream().filter(b -> !chosenTitles.contains(normalizeTitleKey(b.getTitle()))).toList();
            List<Integer> ranked = rankedCandidateIndices(message, "", slot.emotion(), pool);
            int need = Math.min(slot.count(), 3 - result.size());
            int taken = 0;
            for (int idx : ranked) {
                if (taken >= need || result.size() >= 3) break;
                BookDto o = pool.get(idx);
                String key = normalizeTitleKey(o.getTitle());
                if (chosenTitles.contains(key)) continue;
                chosenTitles.add(key);
                result.add(copyBookWithDisplayEmotion(o, null, primaryEmotion));
                taken++;
            }
        }

        // 패스 2: 3권 미만이면 남은 통에서 보충
        if (result.size() < 3) {
            for (EmotionSlot slot : slots) {
                if (result.size() >= 3) break;
                List<BookDto> pool = candidatesByEmotion.getOrDefault(slot.emotion(), List.of())
                        .stream().filter(b -> !chosenTitles.contains(normalizeTitleKey(b.getTitle()))).toList();
                List<Integer> ranked = rankedCandidateIndices(message, "", slot.emotion(), pool);
                for (int idx : ranked) {
                    if (result.size() >= 3) break;
                    BookDto o = pool.get(idx);
                    String key = normalizeTitleKey(o.getTitle());
                    if (chosenTitles.contains(key)) continue;
                    chosenTitles.add(key);
                    result.add(copyBookWithDisplayEmotion(o, null, primaryEmotion));
                }
            }
        }

        // 패스 3: 여전히 미달이면 DB 전체 풀에서 보충 (키워드 없는 입력·NORMAL 통 비어 있음 대응)
        if (result.size() < 3) {
            LinkedHashMap<String, BookDto> byTitle = new LinkedHashMap<>();
            for (List<BookDto> list : candidatesByEmotion.values()) {
                for (BookDto b : list) {
                    String key = normalizeTitleKey(b.getTitle());
                    if (!chosenTitles.contains(key)) byTitle.putIfAbsent(key, b);
                }
            }
            if (byTitle.size() < 3 - result.size()) {
                Set<String> seen = new HashSet<>(chosenTitles);
                for (BookDto b : loadDiverseBookDtos(Set.of(), seen,
                        Objects.hash(message, result.size()), 20)) {
                    byTitle.putIfAbsent(normalizeTitleKey(b.getTitle()), b);
                }
            }
            List<BookDto> global = new ArrayList<>(byTitle.values());
            List<Integer> ranked = rankedCandidateIndices(message, "", EmotionCategory.NORMAL, global);
            for (int idx : ranked) {
                if (result.size() >= 3) break;
                BookDto o = global.get(idx);
                String key = normalizeTitleKey(o.getTitle());
                if (chosenTitles.contains(key)) continue;
                chosenTitles.add(key);
                result.add(copyBookWithDisplayEmotion(o, null, primaryEmotion));
            }
            for (BookDto o : global) {
                if (result.size() >= 3) break;
                String key = normalizeTitleKey(o.getTitle());
                if (chosenTitles.contains(key)) continue;
                chosenTitles.add(key);
                result.add(copyBookWithDisplayEmotion(o, null, primaryEmotion));
            }
        }
        return result;
    }

    private String buildMultiEmotionReason(List<EmotionSlot> slots) {
        if (slots.size() < 2) return slots.isEmpty() ? "" : slots.get(0).emotion().getReason();
        return emotionKo(slots.get(0).emotion()) + "과 " + emotionKo(slots.get(1).emotion())
                + " 감정을 함께 고려해 골라 보았어요.";
    }

    private static String emotionKo(EmotionCategory e) {
        return switch (e) {
            case DEPRESSION -> "우울";
            case STRESS -> "스트레스";
            case ANXIETY -> "불안";
            case LETHARGY -> "무기력";
            case RELATIONSHIP -> "인간관계";
            case NORMAL -> "일반";
        };
    }

    private EmotionCategory resolvePersonalizeEmotion(String message, String emotionParam) {
        if (emotionParam != null && !emotionParam.isBlank()) {
            EmotionCategory e = parseEmotion(emotionParam);
            if (e != EmotionCategory.NORMAL) return e;
        }
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        EmotionCategory kw = inferEmotionFromKeywords(lower);
        if (kw != null) return kw;
        if (geminiClient.isEnabled() && message != null && !message.isBlank()) {
            GeminiAnalysisResult analysis = geminiClient.analyzeUserMessage(message).orElse(null);
            if (analysis != null) {
                EmotionCategory e = parseEmotion(analysis.emotion);
                if (e != EmotionCategory.NORMAL) return e;
            }
        }
        return EmotionCategory.NORMAL;
    }

    /**
     * 네이버 검색어 구성. 자해·위기 표현이 있으면 사용자 원문을 검색어에 섞지 않고,
     * 감정별 안전한 키워드만 사용한다(자극적 단어로 '죽은/법의학'류가 검색되는 것을 막음).
     */
    private String buildAiNaverQuery(EmotionCategory emotion, GeminiAnalysisResult analysis, String userMessage) {
        String anchor = emotion.getSearchQuery();
        String lower = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        if (isSelfHarmCrisisMessage(lower)) {
            String q = (anchor + " 마음안정 위로 희망").trim();
            return q.length() > 130 ? q.substring(0, 130) : q;
        }

        String fromAi = (analysis != null && analysis.searchQuery != null) ? analysis.searchQuery.trim() : "";
        if (fromAi.length() > 90) fromAi = fromAi.substring(0, 90);
        fromAi = sanitizeSearchQueryForNaver(fromAi);

        StringBuilder sb = new StringBuilder();
        if (!fromAi.isBlank()) {
            sb.append(fromAi);
            if (!fromAi.contains(anchor) && sb.length() + anchor.length() + 1 <= 110) {
                sb.append(' ').append(anchor);
            }
        } else {
            sb.append(anchor);
        }
        String snippet = compactUserSnippet(userMessage, 34);
        String safeSnippet = sanitizeSearchQueryForNaver(snippet);
        if (!safeSnippet.isBlank() && sb.indexOf(safeSnippet) < 0 && sb.length() + safeSnippet.length() + 1 <= 120) {
            sb.append(' ').append(safeSnippet);
        }
        String q = sb.toString().trim();
        return q.length() > 130 ? q.substring(0, 130) : q;
    }

    private static String sanitizeSearchQueryForNaver(String s) {
        if (s == null || s.isBlank()) return "";
        String t = s.replaceAll("(?i)자살|자해|스스로.*끝내|목숨.*끊|극단적.?선택", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return t;
    }

    /** 사용자 입력에 자해·위기 신호가 있는지(검색어·점수 로직용). 추천 키워드로 쓰지 않는다. */
    private static boolean isSelfHarmCrisisMessage(String lowerMessage) {
        if (lowerMessage == null || lowerMessage.isBlank()) return false;
        return containsAny(lowerMessage,
                "자살", "죽고 싶", "죽고싶", "끝내고 싶", "끝내고싶", "자해", "극단적 선택", "살기싫", "살기 싫");
    }

    private static final String CRISIS_SUPPORT_MESSAGE_PREFIX =
            "지금 겪는 감정이 매우 무겁게 느껴질 수 있어요. 혼자만 버티지 마시고, "
                    + "24시간 자살예방 상담전화 1393 또는 정신건강 위기 상담 1577-0199, "
                    + "긴급 위기는 119에 연락해 보시길 권합니다.\n\n";

    /** 우울·불안·스트레스·고통 맥락에서 베스트셀러 잡학·클릭베이트로 오인되기 쉬운 도서 */
    private static boolean isMentalHealthRecommendationNoise(BookDto b, String userMessage, EmotionCategory emotion) {
        String t = (b.getTitle() + " " + b.getDescription()).toLowerCase(Locale.ROOT);
        String u = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);
        boolean sensitive = isSelfHarmCrisisMessage(u) || userSoundsEmotionallyDistressed(u)
                || emotion == EmotionCategory.DEPRESSION || emotion == EmotionCategory.ANXIETY
                || emotion == EmotionCategory.STRESS;
        if (!sensitive) return false;
        if (t.contains("죽은 자") || t.contains("법의학") || t.contains("부검") || t.contains("사체")) return true;
        if (t.contains("바보들의 배") || t.contains("핑커 씨") || t.contains("스티븐 핑커") || t.contains("한스 로슬링")) {
            return true;
        }
        return false;
    }

    private static String compactUserSnippet(String userMessage, int maxLen) {
        if (userMessage == null) return "";
        String u = userMessage.replaceAll("[\\n\\r\\t]+", " ").replaceAll("\\s+", " ").trim();
        return u.length() <= maxLen ? u : u.substring(0, maxLen);
    }

    private static String normalizeTitleKey(String title) {
        if (title == null) return "";
        return title.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").trim();
    }

    /** 제목·ISBN 기준 중복 제거하며 네이버 결과를 후보 뒤에 추가 (exclude ISBN은 스킵) */
    private void mergeNaverCandidates(List<BookDto> candidates, List<NaverBookItem> naverItems,
                                      Set<String> excludeIsbns) {
        if (naverItems == null || naverItems.isEmpty()) return;
        Set<String> exclude = excludeIsbns != null ? excludeIsbns : Collections.emptySet();
        Set<String> seen = new HashSet<>();
        for (BookDto b : candidates) {
            seen.add(normalizeTitleKey(b.getTitle()));
        }
        for (NaverBookItem item : naverItems) {
            String title = strip(item.getTitle());
            if (title.isBlank()) continue;
            String key = normalizeTitleKey(title);
            if (seen.contains(key)) continue;
            String isbn13 = extractIsbn13(item.getIsbn());
            if (isbn13 != null && exclude.contains(isbn13)) continue;
            seen.add(key);
            candidates.add(new BookDto(title, item.getAuthor(), item.getPublisher(),
                    item.getLink(), item.getImage(), strip(item.getDescription()),
                    isbn13 != null ? isbn13 : "", ""));
        }
    }

    /**
     * AI 추천으로 확정된 권의 ISBN이 있으면, Gemini가 판정한 bookEmotion으로 INSERT 또는 UPDATE.
     * (GET /api/recommendations 전용 asyncCache와 별개 — AI 경로는 ③단계 태그를 신뢰한다.)
     */
    private void persistBookFromAi(BookDto dto, EmotionCategory bookEmotion, String searchKeyword) {
        if (dto.getIsbn() == null || dto.getIsbn().isBlank()) return;
        CompletableFuture.runAsync(() -> {
            try {
                String isbn = dto.getIsbn();
                Optional<RecommendationBook> existing = repo.findByIsbn(isbn);
                if (existing.isPresent()) {
                    RecommendationBook b = existing.get();
                    b.setEmotion(bookEmotion);
                    b.setSearchKeyword(searchKeyword);
                    repo.save(b);
                    return;
                }
                RecommendationBook book = new RecommendationBook();
                book.setEmotion(bookEmotion);
                book.setTitle(dto.getTitle());
                book.setAuthor(dto.getAuthor());
                book.setPublisher(dto.getPublisher());
                book.setLink(dto.getLink());
                book.setImage(dto.getImage());
                book.setDescription(dto.getDescription());
                book.setIsbn(isbn);
                book.setSearchKeyword(searchKeyword);
                repo.save(book);
            } catch (Exception ignored) {
            }
        });
    }

    /**
     * 감정 확정 우선순위:
     * 1) Gemini 1단계 분석(analysis)이 있으면 그 감정(gemEmotion)을 기본으로 쓴다.
     * 2) Gemini가 NORMAL만 돌려줄 때(모호함)에 한해 메시지 키워드로 보정한다.
     * 3) Gemini 비활성/실패(analysis==null) 시에만 키워드 규칙으로 전체 추론한다.
     */
    private EmotionCategory resolveEmotionForAi(String message, GeminiAnalysisResult analysis, EmotionCategory gemEmotion) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        EmotionCategory kw = inferEmotionFromKeywords(m);

        if (analysis != null) {
            if (gemEmotion != EmotionCategory.NORMAL) {
                return gemEmotion;
            }
            if (kw != null) {
                return kw;
            }
            return EmotionCategory.NORMAL;
        }
        if (kw != null) {
            return kw;
        }
        return gemEmotion;
    }

    /** 한글/영문 키워드로 감정 추정 */
    private EmotionCategory inferEmotionFromKeywords(String lowerMessage) {
        if (lowerMessage == null || lowerMessage.isBlank()) return null;
        if (containsAny(lowerMessage, "우울", "depression", "절망", "허탈", "허무",
                "힘들", "괴로", "지쳐", "슬프", "슬퍼", "막막", "외롭")) return EmotionCategory.DEPRESSION;
        if (containsAny(lowerMessage, "스트레스", "stress")) return EmotionCategory.STRESS;
        if (containsAny(lowerMessage, "불안", "anxiety", "불안장애", "초조", "불면", "답답", "긴장", "공황")) return EmotionCategory.ANXIETY;
        if (containsAny(lowerMessage, "무기력", "lethargy", "번아웃", "burnout", "의욕", "나태", "게으름")) return EmotionCategory.LETHARGY;
        if (containsAny(lowerMessage, "관계", "친구", "가족", "연인", "relationship", "소통", "외로", "이별", "갈등")) return EmotionCategory.RELATIONSHIP;
        return null;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) {
            if (haystack.contains(n.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /** 네이버 isbn 필드("ISBN10 ISBN13")에서 13자리 숫자 추출 */
    private String extractIsbn13(String raw) {
        if (raw == null || raw.isBlank()) return null;
        for (String part : raw.trim().split("\\s+")) {
            if (part.length() == 13 && part.matches("\\d+")) return part;
        }
        return null;
    }

    private String strip(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * 카드에 표시할 권별 감정 태그. DB 후보의 bookEmotion을 우선하고,
     * 사용자 세션 감정(NORMAL 등)으로 덮어쓰지 않는다.
     */
    private BookDto copyBookWithDisplayEmotion(BookDto source, String geminiTag, EmotionCategory sessionEmotion) {
        String display = resolveDisplayBookEmotion(source.getBookEmotion(), geminiTag, sessionEmotion);
        return new BookDto(source.getTitle(), source.getAuthor(), source.getPublisher(),
                source.getLink(), source.getImage(), source.getDescription(), source.getIsbn(), display);
    }

    private static String resolveDisplayBookEmotion(String dbBookEmotion, String geminiTag, EmotionCategory sessionEmotion) {
        if (dbBookEmotion != null && !dbBookEmotion.isBlank()) {
            return dbBookEmotion.trim().toUpperCase(Locale.ROOT);
        }
        if (geminiTag != null && !geminiTag.isBlank()) {
            try {
                return EmotionCategory.valueOf(geminiTag.trim().toUpperCase(Locale.ROOT)).name();
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return sessionEmotion.name();
    }

    private EmotionCategory parseEmotion(String s) {
        if (s == null || s.isBlank()) return EmotionCategory.NORMAL;
        try {
            return EmotionCategory.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EmotionCategory.NORMAL;
        }
    }
}
