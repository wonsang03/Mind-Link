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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * ============================================================
 *  맞춤 도서 추천 기능 — 통합 단일 파일
 *  병합 전 반드시 MERGE_NOTES.md 확인
 * ============================================================
 * [핵심 로직]
 *  1. 감정 태그로 자체 DB 우선 조회
 *  2. DB 결과 있음 → Gemini 위로 멘트 생성 (없으면 기본값)
 *  3. DB 결과 0건 → 네이버 도서 API 실시간 호출
 *  4. 네이버 신규 도서(ISBN 미보유) → Gemini 감정 태그 분석 후 비동기 DB 캐싱
 *  5. 최종 응답: 엄격한 JSON 구조 유지 (emotion/reason/books/count/source)
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
 * 10.  RecommendationService        — 핵심 로직 (DB→Gemini→Naver→캐싱)
 * 11.  RecommendationController     — GET /api/recommendations
 * ============================================================
 * [도서 데이터 관리]
 *  초기 적재: ORACLE_SEED.sql 최초 1회 수동 실행
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
    @Column(nullable = false)
    private String description;

    // 네이버 API 자동 캐싱 시 중복 방지 기준 (수동 등록 도서는 NULL 허용)
    @Column(length = 50, unique = true)
    private String isbn;
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

    BookDto(String title, String author, String publisher,
            String link, String image, String description) {
        this.title       = strip(title);
        this.author      = strip(author);
        this.publisher   = publisher   == null ? "" : publisher;
        this.link        = link        == null ? "" : link;
        this.image       = image       == null ? "" : image;
        this.description = strip(description);
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
        if ("NOT_SET".equals(clientId) || "NOT_SET".equals(clientSecret)) return List.of();
        try {
            String enc = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url  = API_URL + "?query=" + enc + "&display=" + size + "&start=1&sort=sim";
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
//    - generateComfortMessage: 위로 멘트 생성
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

    /** 감정 상태에 맞는 위로 멘트 생성 */
    Optional<String> generateComfortMessage(String emotionName) {
        if (!isEnabled()) return Optional.empty();
        String prompt = "사용자의 현재 감정 상태는 " + emotionName + "입니다.\n"
                + "3문장 이내의 따뜻하고 공감 가는 한국어 위로 멘트를 작성해주세요. "
                + "마크다운 없이 순수 텍스트로만 답변하세요.";
        return call(prompt);
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

    private Optional<String> call(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
            );
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
                            b.getLink(), b.getImage(), b.getDescription()))
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
                asyncCache(item, isbn, emotion);
            }
            return new BookDto(item.getTitle(), item.getAuthor(), item.getPublisher(),
                    item.getLink(), item.getImage(), item.getDescription());
        }).toList();

        String reason = geminiClient.generateComfortMessage(emotion.name())
                .orElse(emotion.getReason());
        return new RecommendationResponse(emotion.name(), reason, books, "NAVER");
    }

    /** 네이버 신규 도서를 비동기로 Gemini 분류 후 DB INSERT */
    private void asyncCache(NaverBookItem item, String isbn, EmotionCategory fallback) {
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
                repo.save(book);
            } catch (Exception ignored) {} // unique 제약 위반 등 무시
        });
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
}
