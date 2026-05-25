package com.mindlink.recommendation.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindlink.recommendation.EmotionCategory;
import com.mindlink.recommendation.dto.BookDto;
import com.mindlink.recommendation.dto.EmotionSlot;
import com.mindlink.recommendation.dto.GeminiAnalysisResult;
import com.mindlink.recommendation.dto.GeminiSelectionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Google Gemini AI 클라이언트.
 *  - analyzeUserMessage:     [AI ①] 사용자 상태 판단 전용 (도서 추천 금지)
 *  - selectBooksWithReason:  [AI ③] API 후보목록에서만 선별
 *  - generateComfortMessage: 위로 멘트
 *  - extractEmotionTag:      책 설명 → 감정 태그 자동 분류
 * 키 미설정(NOT_SET) 시 모든 메서드 Optional.empty() 반환 → 자동 fallback.
 */
@Component
public class GeminiClient {

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

    public boolean isEnabled() { return !"NOT_SET".equals(apiKey); }

    /**
     * [AI 파이프라인 ①단계] 사용자 자유 메시지 → 상태 판단만 수행.
     * 도서 제목을 추천하거나 나열하지 말 것. 이후 단계에서 검색 API가 책 목록을 가져온다.
     */
    public Optional<GeminiAnalysisResult> analyzeUserMessage(String message) {
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
    public Optional<GeminiSelectionResult> selectBooksWithReason(
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
    public Optional<String> generateComfortMessage(String emotionName) {
        return generateComfortMessage(emotionName, null);
    }

    /** 감정 + 사용자가 직접 쓴 문장을 반영한 위로 멘트 (AI 추천 경로용) */
    public Optional<String> generateComfortMessage(String emotionName, String userMessage) {
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

    /**
     * 복합 감정 슬롯 결정 (합 = 3).
     * 감정 2개: 2+1 또는 1+2 / 감정 1개: count=3 / 0개: [{NORMAL,3}]
     */
    public Optional<List<EmotionSlot>> resolveRecommendationSlots(String message, List<EmotionCategory> detected) {
        if (!isEnabled()) return Optional.empty();
        // 키워드 없음 → 서버 fallback(감정 3종 1+1+1) 사용
        if (detected.isEmpty()) return Optional.empty();
        String detectedStr = detected.stream().map(EmotionCategory::name)
                .collect(java.util.stream.Collectors.joining(", "));
        String prompt = "역할: 도서 추천 슬롯 결정기. 사용자 메시지와 탐지된 감정을 보고 3권 슬롯을 정한다.\n"
                + "사용자 메시지: \"" + message.replace("\"", "'") + "\"\n"
                + "탐지된 감정(키워드): " + detectedStr + "\n\n"
                + "규칙:\n"
                + "- slots[].count 합 반드시 3\n"
                + "- 감정 1개 → [{emotion,count:3}]\n"
                + "- 감정 2개 → 2+1 또는 1+2만 허용 (1+1+1 금지)\n"
                + "- emotion 값: DEPRESSION, STRESS, ANXIETY, LETHARGY, RELATIONSHIP, NORMAL\n"
                + "- searchQuery 생성 금지\n\n"
                + "다음 JSON으로만 응답:\n"
                + "{\"slots\":[{\"emotion\":\"ANXIETY\",\"count\":2},{\"emotion\":\"STRESS\",\"count\":1}],"
                + "\"primaryEmotion\":\"ANXIETY\"}";
        return call(prompt, 0.2).flatMap(text -> {
            try {
                var node = MAPPER.readTree(extractJson(text));
                List<EmotionSlot> slots = new ArrayList<>();
                node.path("slots").forEach(s -> {
                    String em = s.path("emotion").asText("").trim().toUpperCase(Locale.ROOT);
                    int cnt = s.path("count").asInt(0);
                    if (VALID_TAGS.contains(em) && cnt > 0) {
                        try { slots.add(new EmotionSlot(EmotionCategory.valueOf(em), cnt)); }
                        catch (IllegalArgumentException ignored) {}
                    }
                });
                if (slots.isEmpty()) return Optional.empty();
                // 합이 3이 아니면 마지막 슬롯에서 조정
                int total = slots.stream().mapToInt(EmotionSlot::count).sum();
                if (total != 3) {
                    int deficit = 3 - total;
                    EmotionSlot last = slots.get(slots.size() - 1);
                    slots.set(slots.size() - 1, new EmotionSlot(last.emotion(), Math.max(1, last.count() + deficit)));
                }
                return Optional.of(slots);
            } catch (Exception e) { return Optional.empty(); }
        });
    }

    /** 책 제목·설명 분석 → 감정 태그 자동 추출 */
    public Optional<EmotionCategory> extractEmotionTag(String title, String description) {
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
