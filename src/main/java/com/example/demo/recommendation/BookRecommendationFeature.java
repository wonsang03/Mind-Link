package com.example.demo.recommendation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ============================================================
 *  맞춤 도서 추천 기능 — 통합 단일 파일
 *  병합 전 반드시 MERGE_NOTES.md 확인
 * ============================================================
 * [핵심 로직]
 *  1. 감정 태그로 자체 DB 우선 조회
 *  2. DB 결과 있음 → (AI 경로) 사용자·Gemini 검색어로 네이버 후보를 합쳐 다양화 후 선별
 *  3. DB 결과 0건 → 네이버 도서 API (Gemini searchQuery + 감정 키워드 + 메시지 일부)
 *  4. 네이버 신규 도서(ISBN 미보유) — GET 추천 경로에서만 비동기 Gemini 태그 + DB 캐싱.
 *     POST /api/recommendations/ai 는 ③단계에서 권별 태그 확정 후 DB에 INSERT/UPDATE.
 *  5. 최종 응답: 엄격한 JSON 구조 유지 (emotion/reason/books/count/source)
 *
 * [POST /api/recommendations/ai — 3단계 파이프라인]
 *  ① Gemini: 사용자 입력만 보고 상태(감정 태그)·검색어·한 줄 요약 판단
 *  ② 서버: 키워드로 감정 보정 후, 판단 결과를 기준으로 DB·네이버 검색 API로 후보만 수집
 *  ③ Gemini: 수집된 목록만 읽고 최대 3권 인덱스 + 권별 감정 태그(bookTags) + 추천 이유 확정 (목록 밖 책 금지)
 * ============================================================
 * [파일 내 클래스 순서]
 *  1.  EmotionCategory              — 감정 → 검색키워드 + 기본 위로 멘트
 *  2.  RecommendationBook           — DB 엔티티 (recommendation_books 테이블)
 *  3.  RecommendationBookRepository — JPA Repository
 *  4.  NaverBookItem                — 네이버 API 결과 DTO
 *  5.  NaverBookSearchResponse      — 네이버 API 역직렬화용
 *  6.  BookDto                      — 최종 응답 DTO (도서 1건)
 *  7.  RecommendationResponse       — 최종 응답 JSON 구조
 *  8.  NaverBookApiClient           — 네이버 도서 검색 HTTP 클라이언트
 *  9.  GeminiClient                 — Google Gemini AI 클라이언트
 * 10.  RecommendationService        — 핵심 로직 (일반 GET: DB→네이버 / AI POST: ①판단→②검색→③선별)
 * 11.  RecommendationController     — GET /api/recommendations
 * ============================================================
 * [도서 데이터 관리]
 *  초기 적재: sql/ORACLE_SETUP.sql 수동 실행(파일 주석 참고)
 *  추가·수정: Oracle에 직접 INSERT/UPDATE
 *  자동 캐싱: 네이버 신규 도서 → 비동기 INSERT (isbn 기준 중복 방지)
 * ============================================================
 */

// ============================================================
// 1. Enum: 감정 상태 → 네이버 검색키워드 + 기본 위로 멘트
// ============================================================
enum EmotionCategory {

    DEPRESSION("위로 감성 에세이",
            "지금 많이 힘드시겠어요. 따뜻한 위로가 되는 책들을 추천해드려요."),
    STRESS("스트레스 해소 마인드풀니스",
            "스트레스가 많이 쌓이셨군요. 마음을 편안하게 해주는 책들을 추천해드려요."),
    ANXIETY("불안 극복 마음챙김",
            "불안한 마음이 드시나요? 마음을 안정시키는 데 도움이 되는 책들을 추천해드려요."),
    LETHARGY("동기부여 자기계발",
            "의욕이 생기지 않으실 때, 다시 힘을 내게 해주는 책들을 추천해드려요."),
    RELATIONSHIP("인간관계 소통 공감",
            "관계 속에서 지친 마음을 보듬고 건강한 대화를 돕는 책들을 추천해드려요."),
    NORMAL("베스트셀러 교양",
            "현재 마음 상태가 안정적이에요. 다양한 분야를 넓혀갈 좋은 책들을 추천해드려요.");

    private final String searchQuery;
    private final String reason;

    EmotionCategory(String searchQuery, String reason) {
        this.searchQuery = searchQuery;
        this.reason = reason;
    }

    public String getSearchQuery() { return searchQuery; }
    public String getReason()      { return reason; }
}

// ============================================================
// 2. Entity: 추천 도서 (recommendation_books 테이블)
//    Hibernate ddl-auto=update → Oracle에 테이블 자동 생성
// ============================================================
@Entity
@Table(name = "recommendation_books")
@Getter @Setter @NoArgsConstructor
class RecommendationBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmotionCategory emotion;

    @Column(nullable = false, length = 1000)
    private String title;

    @Column(length = 500)
    private String author;
    @Column(length = 500)
    private String publisher;
    @Column(length = 2000)
    private String link;

    @Column(length = 2000)
    private String image;

    @Lob
    @Column(nullable = true)
    private String description;

    // 네이버 API 자동 캐싱 시 중복 방지 기준 (수동 등록 도서는 NULL 허용)
    @Column(length = 50, unique = true)
    private String isbn;

    // 캐싱 당시 사용된 검색 키워드 (Gemini 생성 포함)
    @Column(name = "search_keyword", length = 500)
    private String searchKeyword;
}

// ============================================================
// 3. Repository
// ============================================================
@Repository
interface RecommendationBookRepository extends JpaRepository<RecommendationBook, Long> {
    List<RecommendationBook> findByEmotion(EmotionCategory emotion);
    Optional<RecommendationBook> findByIsbn(String isbn);
}

// ============================================================
// 4. DTO: 네이버 API 결과 단건
// ============================================================
@Getter @Setter
class NaverBookItem {
    private String title;
    private String link;
    private String image;
    private String author;
    private String publisher;
    private String description;
    private String isbn;  // "ISBN10 ISBN13" 형식 (공백 구분)
}

// ============================================================
// 5. DTO: 네이버 API 응답 역직렬화
// ============================================================
@Getter @Setter
class NaverBookSearchResponse {
    private List<NaverBookItem> items;
}

// ============================================================
// 6. DTO: 최종 응답 — 도서 1건
// ============================================================
@Getter
class BookDto {
    private final String title;
    private final String author;
    private final String publisher;
    private final String link;
    private final String image;
    private final String description;
    /** ISBN13 (없으면 빈 문자열). AI·네이버 캐시 시 DB 키로 사용 */
    private final String isbn;
    /**
     * 이 책에 대한 감정 태그(DEPRESSION 등). ③단계 Gemini가 권별로 판정하면 설정되고,
     * DB에서 내려줄 때는 해당 행의 emotion과 동일하게 둔다.
     */
    private final String bookEmotion;

    BookDto(String title, String author, String publisher,
            String link, String image, String description) {
        this(title, author, publisher, link, image, description, "", "");
    }

    BookDto(String title, String author, String publisher,
            String link, String image, String description, String isbn, String bookEmotion) {
        this.title       = strip(title);
        this.author      = strip(author);
        this.publisher   = publisher   == null ? "" : publisher;
        this.link        = link        == null ? "" : link;
        this.image       = image       == null ? "" : image;
        this.description = strip(description);
        this.isbn        = isbn == null ? "" : isbn.trim();
        this.bookEmotion = bookEmotion == null || bookEmotion.isBlank() ? "" : bookEmotion.trim().toUpperCase(Locale.ROOT);
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("<[^>]*>", "").trim();
    }
}

// ============================================================
// 7. DTO: 최종 응답 JSON (엄격한 구조)
//    { emotion, reason, books, count, source }
// ============================================================
@Getter
class RecommendationResponse {
    private final String emotion;      // 감정 태그 (DEPRESSION 등)
    private final String reason;       // 위로 멘트 (Gemini 우선, 기본값 fallback)
    private final List<BookDto> books;
    private final int count;
    private final String source;       // "DB" | "NAVER" | "EMPTY"

    RecommendationResponse(String emotion, String reason, List<BookDto> books, String source) {
        this.emotion = emotion;
        this.reason  = reason != null ? reason : "";
        this.books   = books  != null ? books  : List.of();
        this.count   = this.books.size();
        this.source  = source != null ? source : "EMPTY";
    }
}

// ============================================================
// 8. 네이버 도서 검색 API 클라이언트 (DB 결과 없을 때 fallback)
// ============================================================
@Component
class NaverBookApiClient {

    private static final String API_URL = "https://openapi.naver.com/v1/search/book.json";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${naver.api.client-id:NOT_SET}")
    private String clientId;

    @Value("${naver.api.client-secret:NOT_SET}")
    private String clientSecret;

    List<NaverBookItem> search(String query, int size) {
        return search(query, size, 1, "sim");
    }

    /**
     * @param start 네이버 API start (1 기반, 최대 1000)
     * @param sort  sim(정확도순) | date(출간일) | count(판매순)
     */
    List<NaverBookItem> search(String query, int display, int start, String sort) {
        if ("NOT_SET".equals(clientId) || "NOT_SET".equals(clientSecret)) return List.of();
        try {
            int disp = Math.min(100, Math.max(1, display));
            int st = Math.max(1, Math.min(start, 1000));
            String srt = sort == null || sort.isBlank() ? "sim" : sort.trim().toLowerCase(Locale.ROOT);
            if (!"sim".equals(srt) && !"date".equals(srt) && !"count".equals(srt)) {
                srt = "sim";
            }
            String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = API_URL + "?query=" + enc + "&display=" + disp + "&start=" + st + "&sort=" + srt;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Naver-Client-Id",     clientId)
                    .header("X-Naver-Client-Secret", clientSecret)
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return List.of();
            NaverBookSearchResponse parsed = MAPPER.readValue(resp.body(), NaverBookSearchResponse.class);
            return parsed.getItems() != null ? parsed.getItems() : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}

// ============================================================
// 9. Google Gemini AI 클라이언트
//    - analyzeUserMessage:     [AI ①] 사용자 상태 판단 전용 (도서 추천 금지)
//    - selectBooksWithReason:  [AI ③] API 후보목록에서만 선별
//    - generateComfortMessage: 위로 멘트
//    - extractEmotionTag:      책 설명 → 감정 태그 자동 분류
//    키 미설정(NOT_SET) 시 모든 메서드 Optional.empty() 반환 → 자동 fallback
// ============================================================
@Component
class GeminiClient {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final Set<String> VALID_TAGS =
            Set.of("DEPRESSION", "STRESS", "ANXIETY", "LETHARGY", "RELATIONSHIP", "NORMAL");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${gemini.api.key:NOT_SET}")
    private String apiKey;

    boolean isEnabled() { return !"NOT_SET".equals(apiKey); }

    /**
     * [AI 파이프라인 ①단계] 사용자 자유 메시지 → 상태 판단만 수행.
     * 도서 제목을 추천하거나 나열하지 말 것. 이후 단계에서 검색 API가 책 목록을 가져온다.
     */
    Optional<GeminiAnalysisResult> analyzeUserMessage(String message) {
        if (!isEnabled()) return Optional.empty();
        String prompt = "역할: 너는 도서관 맞춤 추천 시스템의 **1단계 상태 분석기**다. 아직 책을 고르지 않는다.\n"
                + "사용자 메시지: \"" + message.replace("\"", "'") + "\"\n\n"
                + "할 일:\n"
                + "1) 위 메시지를 근거로 현재 심리·정서 상태를 가장 가까운 태그 하나로 분류한다.\n"
                + "   짧은 표현(예: '힘들어', '우울', '불안', '답답')만 있어도 반드시 DEPRESSION/STRESS/ANXIETY 등에 매핑한다. "
                + "NORMAL은 '별일 없음', '그냥 궁금', '책 추천만'처럼 정서적으로 무탈할 때만 사용한다.\n"
                + "2) 다음 검색 단계에서 네이버 도서 API에 넣을 **한국어 검색어**를 만든다. "
                + "사용자의 구체 상황·주제(직장, 관계, 수면, 불안 등)가 드러나게 3~8단어로 쓴다. "
                + "**같은 짧은 표현만 반복되는 입력이라도**, 그 안에서 구체화할 수 있는 단어(예: 언제, 무엇 때문에)를 추정해 검색어를 **매번 조금씩 다르게** 만든다. "
                + "단, searchQuery에는 **자살·자해·극단적 선택 등 직접적 단어를 절대 넣지 말고**, "
                + "'심리 위로 마음안정 희망'처럼 안전한 표현만 사용한다.\n"
                + "3) 사용자 상태를 한 줄로 요약한다.\n\n"
                + "금지: 책 제목 추천, 목록 나열, 위로 멘트 작성.\n\n"
                + "다음 JSON 형식으로만 응답하세요 (다른 텍스트 없이):\n"
                + "{\"emotion\":\"DEPRESSION|STRESS|ANXIETY|LETHARGY|RELATIONSHIP|NORMAL 중 정확히 하나\","
                + "\"searchQuery\":\"네이버 도서 검색용 한국어 키워드(3~8단어, 심리·상황 반영)\","
                + "\"summary\":\"사용자 상태 한 줄 요약\"}";
        return call(prompt, 0.35).flatMap(text -> {
            try {
                var node = MAPPER.readTree(extractJson(text));
                GeminiAnalysisResult r = new GeminiAnalysisResult();
                r.emotion     = node.path("emotion").asText("NORMAL").trim().toUpperCase();
                if (!VALID_TAGS.contains(r.emotion)) r.emotion = "NORMAL";
                r.searchQuery = node.path("searchQuery").asText("").trim();
                r.summary     = node.path("summary").asText("").trim();
                return Optional.of(r);
            } catch (Exception e) { return Optional.empty(); }
        });
    }

    /**
     * [AI 파이프라인 ③단계] 검색 API로 모은 후보목록만 보고 선별.
     * 목록에 없는 책 제목을 지어내거나 JSON 밖에 언급하지 말 것. 오직 selectedIndices와 reason만 출력.
     */
    Optional<GeminiSelectionResult> selectBooksWithReason(
            String userMessage, String userSummary, String resolvedEmotion, List<BookDto> candidates) {
        if (!isEnabled() || candidates.isEmpty()) return Optional.empty();
        var sb = new StringBuilder();
        sb.append("역할: 너는 **3단계 최종 선별기**다. 이미 서버가 검색 API로 아래 **번호 붙은 목록**만 가져왔다. ")
          .append("목록에 없는 책은 존재하지 않는 것으로 취급하고, 절대 지어내지 않는다.\n");
        sb.append("사용자 메시지: \"").append(userMessage.replace("\"", "'")).append("\"\n");
        if (!userSummary.isBlank())
            sb.append("사용자 상태 요약(1단계): ").append(userSummary).append("\n");
        sb.append("확정 감정 태그: ").append(resolvedEmotion).append("\n");
        sb.append("\n아래 **번호 0~").append(candidates.size() - 1).append(" 도서만** 후보다. ")
          .append("사용자 메시지·요약과 직접 연결되는 책만 최대 3권, selectedIndices에 그 번호만 넣어라.\n")
          .append("- **개인화**: reason과 선택에서 사용자 메시지·요약에 나온 **구체 단어**(직장, 연애, 가족, 수면, 시험 등)를 반드시 반영한다. 일반론만 쓰지 말 것.\n")
          .append("- **다양성**: 비슷한 베스트셀러·잡학 교양만 고르지 말고, 서로 **다른 각도**(예: 이해·실천·이야기/에세이)가 되도록 3권을 조합한다. 제목만 비슷한 책을 나란히 고르지 말 것.\n")
          .append("- 추천 이유(reason): 선정한 각 번호의 책이 사용자 문장과 어떻게 맞는지 한두 문장으로 연결할 것.\n")
          .append("- 태그 ").append(resolvedEmotion).append("에 맞지 않거나 심리·위로·자기이해·관계와 무관하면 넣지 말 것.\n")
          .append("- 적합한 책이 없으면 selectedIndices는 [] 이고 reason에 이유만 쓸 것.\n\n도서 목록:\n");
        for (int i = 0; i < candidates.size(); i++) {
            BookDto b = candidates.get(i);
            String desc = b.getDescription();
            sb.append(i).append(". 제목: ").append(b.getTitle());
            if (!desc.isBlank())
                sb.append(" / 설명: ").append(desc, 0, Math.min(220, desc.length()));
            sb.append("\n");
        }
        sb.append("\n다음 JSON 형식으로만 응답하세요 (다른 텍스트 없이):\n")
          .append("{\"reason\":\"…\",\"selectedIndices\":[0,1,2],")
          .append("\"bookTags\":[{\"index\":0,\"emotion\":\"DEPRESSION\"},{\"index\":1,\"emotion\":\"ANXIETY\"},{\"index\":2,\"emotion\":\"STRESS\"}]}")
          .append("\n규칙: bookTags에는 selectedIndices에 넣은 **각 번호마다 정확히 하나**의 emotion을 넣는다. ")
          .append("emotion 값은 DEPRESSION, STRESS, ANXIETY, LETHARGY, RELATIONSHIP, NORMAL 중 하나만 사용한다. ")
          .append("각 책 제목·설명을 보고 그 책이 돕는 심리 맥락에 맞는 태그를 고른다(모두 같은 태그로만 채우지 말 것).");
        return call(sb.toString(), 0.32).flatMap(text -> {
            try {
                var node = MAPPER.readTree(extractJson(text));
                GeminiSelectionResult r = new GeminiSelectionResult();
                r.reason = node.path("reason").asText("").trim();
                r.selectedIndices = new ArrayList<>();
                node.path("selectedIndices").forEach(n -> {
                    int idx = n.asInt(-1);
                    if (idx >= 0) r.selectedIndices.add(idx);
                });
                r.bookEmotionsByIndex = new LinkedHashMap<>();
                node.path("bookTags").forEach(n -> {
                    int idx = n.path("index").asInt(-1);
                    String em = n.path("emotion").asText("").trim().toUpperCase(Locale.ROOT);
                    if (idx >= 0 && VALID_TAGS.contains(em)) {
                        r.bookEmotionsByIndex.put(idx, em);
                    }
                });
                return Optional.of(r);
            } catch (Exception e) { return Optional.empty(); }
        });
    }

    /** 감정 상태에 맞는 위로 멘트 생성 */
    Optional<String> generateComfortMessage(String emotionName) {
        return generateComfortMessage(emotionName, null);
    }

    /** 감정 + 사용자가 직접 쓴 문장을 반영한 위로 멘트 (AI 추천 경로용) */
    Optional<String> generateComfortMessage(String emotionName, String userMessage) {
        if (!isEnabled()) return Optional.empty();
        var sb = new StringBuilder();
        sb.append("사용자의 현재 감정 상태는 ").append(emotionName).append("입니다.\n");
        if (userMessage != null && !userMessage.isBlank()) {
            sb.append("사용자 메시지: \"").append(userMessage.replace("\"", "'").trim()).append("\"\n");
        }
        sb.append("위 내용을 반영해 3문장 이내의 따뜻하고 공감 가는 한국어 멘트를 작성하세요. ")
          .append("반드시 사용자가 겪는 상황을 짚어 주고, 일반론만 나열하지 마세요. ")
          .append("마크다운 없이 순수 텍스트로만 답변하세요.");
        return call(sb.toString());
    }

    /** 책 제목·설명 분석 → 감정 태그 자동 추출 */
    Optional<EmotionCategory> extractEmotionTag(String title, String description) {
        if (!isEnabled()) return Optional.empty();
        String prompt = "다음 책의 감정 태그를 DEPRESSION, STRESS, ANXIETY, LETHARGY, RELATIONSHIP, NORMAL 중 "
                + "정확히 하나만 영어 대문자로 답하세요.\n"
                + "제목: " + title + "\n설명: " + description;
        return call(prompt)
                .map(s -> s.trim().toUpperCase())
                .filter(VALID_TAGS::contains)
                .map(s -> {
                    try { return EmotionCategory.valueOf(s); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(Objects::nonNull);
    }

    private String extractJson(String text) {
        int s = text.indexOf('{');
        int e = text.lastIndexOf('}');
        return (s >= 0 && e > s) ? text.substring(s, e + 1) : text;
    }

    /** temperature null이면 API 기본값 사용 */
    private Optional<String> call(String prompt, Double temperature) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            if (temperature != null) {
                body.put("generationConfig", Map.of("temperature", temperature));
            }
            String bodyJson = MAPPER.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + "?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return Optional.empty();
            String text = MAPPER.readTree(resp.body())
                    .path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText("");
            return text.isBlank() ? Optional.empty() : Optional.of(text.trim());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> call(String prompt) {
        return call(prompt, null);
    }
}

// ============================================================
// 10a. 내부 전송 DTO (GeminiClient ↔ RecommendationService)
// ============================================================
class GeminiAnalysisResult {
    String emotion;      // 6개 태그 중 하나
    String searchQuery;  // 네이버 검색용 키워드
    String summary;      // 사용자 상태 한 줄 요약
}

class GeminiSelectionResult {
    String reason;                 // 사용자에게 보여줄 추천 이유
    List<Integer> selectedIndices; // 후보 목록 내 선택된 0-based 인덱스
    /** 후보 인덱스 → Gemini가 판정한 권별 감정 태그(영문) */
    Map<Integer, String> bookEmotionsByIndex = new LinkedHashMap<>();
}

// ============================================================
// 10b. AI 추천 요청 DTO
// ============================================================
@Getter @Setter
class AiRecommendationRequest {
    private String message;
}

// ============================================================
// 10. Service — 핵심 로직
// ============================================================
@Service
class RecommendationService {

    private final RecommendationBookRepository repo;
    private final NaverBookApiClient           naverClient;
    private final GeminiClient                 geminiClient;

    RecommendationService(RecommendationBookRepository repo,
                          NaverBookApiClient naverClient,
                          GeminiClient geminiClient) {
        this.repo         = repo;
        this.naverClient  = naverClient;
        this.geminiClient = geminiClient;
    }

    /**
     * TODO [다른 팀 협의 필요 — 테스트용 구현]
     *  현재: 프론트가 emotion 쿼리 파라미터를 문자열로 전달
     *  예) GET /api/recommendations?emotion=DEPRESSION&size=5
     *  설문 모듈의 결과값 형식이 확정되면 parseEmotion() 수정 필요
     */
    RecommendationResponse getRecommendations(String emotionStr, int size) {
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

        // ── 2단계: DB 0건 → 네이버 API 실시간 호출 ────────────────
        List<NaverBookItem> naverItems = naverClient.search(emotion.getSearchQuery(), size);

        if (naverItems.isEmpty()) {
            String reason = geminiClient.generateComfortMessage(emotion.name())
                    .orElse(emotion.getReason());
            return new RecommendationResponse(emotion.name(), reason, List.of(), "EMPTY");
        }

        // ── 3단계: 신규 ISBN → 비동기 Gemini 태그 분석 + DB 캐싱 ──
        List<BookDto> books = naverItems.stream().map(item -> {
            String isbn = extractIsbn13(item.getIsbn());
            if (isbn != null && repo.findByIsbn(isbn).isEmpty()) {
                asyncCache(item, isbn, emotion, emotion.getSearchQuery());
            }
            return new BookDto(item.getTitle(), item.getAuthor(), item.getPublisher(),
                    item.getLink(), item.getImage(), item.getDescription(),
                    isbn != null ? isbn : "", "");
        }).toList();

        String reason = geminiClient.generateComfortMessage(emotion.name())
                .orElse(emotion.getReason());
        return new RecommendationResponse(emotion.name(), reason, books, "NAVER");
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
            String te = tagByIndex.get(idx);
            EmotionCategory perBook = (te != null && !te.isBlank()) ? parseEmotion(te) : emotion;
            finalBooks.add(new BookDto(o.getTitle(), o.getAuthor(), o.getPublisher(), o.getLink(), o.getImage(),
                    o.getDescription(), o.getIsbn(), perBook.name()));
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
    RecommendationResponse getAiRecommendations(String userMessage, List<String> excludeRecentIsbns) {
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

    private EmotionCategory parseEmotion(String s) {
        if (s == null || s.isBlank()) return EmotionCategory.NORMAL;
        try {
            return EmotionCategory.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EmotionCategory.NORMAL;
        }
    }
}

// ============================================================
// 11. Controller
// ============================================================
@RestController
@RequestMapping("/api/recommendations")
class RecommendationController {

    private static final String SESSION_AI_ISBN_HISTORY = "ml_ai_recent_isbns";

    private final RecommendationService service;

    RecommendationController(RecommendationService service) {
        this.service = service;
    }

    /**
     * GET /api/recommendations?emotion=DEPRESSION&size=5
     *
     * @param emotion 감정 상태 (DEPRESSION / STRESS / ANXIETY / LETHARGY / RELATIONSHIP / NORMAL)
     * @param size    반환 도서 수 (최대 20)
     *
     * TODO [다른 팀 협의 필요] JWT 인증 연동 시 SecurityConfig에서 이 경로에 인증 요구 추가
     */
    @GetMapping
    RecommendationResponse getRecommendations(
            @RequestParam(defaultValue = "NORMAL") String emotion,
            @RequestParam(defaultValue = "5")      int size) {
        return service.getRecommendations(emotion, Math.min(Math.max(size, 1), 20));
    }

    /** POST /api/recommendations/ai  body: { "message": "..." } — 세션에 최근 추천 ISBN을 쌓아 반복 완화 */
    @PostMapping("/ai")
    RecommendationResponse getAiRecommendations(@RequestBody AiRecommendationRequest req, HttpSession session) {
        String msg = req.getMessage();
        if (msg == null || msg.isBlank())
            return new RecommendationResponse("NORMAL", "메시지를 입력해 주세요.", List.of(), "EMPTY");
        String safe = msg.trim();
        if (safe.length() > 500) safe = safe.substring(0, 500);

        @SuppressWarnings("unchecked")
        List<String> hist = (List<String>) session.getAttribute(SESSION_AI_ISBN_HISTORY);
        List<String> exclude = new ArrayList<>();
        if (hist != null) {
            int from = Math.max(0, hist.size() - 35);
            exclude.addAll(hist.subList(from, hist.size()));
        }

        RecommendationResponse resp = service.getAiRecommendations(safe, exclude);

        if (resp.getBooks() != null && !resp.getBooks().isEmpty()) {
            List<String> next = new ArrayList<>(exclude);
            for (BookDto b : resp.getBooks()) {
                if (b.getIsbn() != null && !b.getIsbn().isBlank()) {
                    next.add(b.getIsbn());
                }
            }
            final int maxHist = 40;
            while (next.size() > maxHist) {
                next.remove(0);
            }
            session.setAttribute(SESSION_AI_ISBN_HISTORY, next);
        }
        return resp;
    }
}
