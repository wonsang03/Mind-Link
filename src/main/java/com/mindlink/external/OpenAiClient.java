package com.mindlink.external;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAI Chat Completions API 클라이언트.
 * .env 의 OPENAI_API_KEY 와 application.properties 의 openai.model 을 사용한다.
 */
@Component
public class OpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClient.class);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${openai.api.key:OPENAI_KEY_NOT_SET}")
    private String apiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    /** 가장 저렴한 일반 채팅 모델 (테스트·개발용) */
    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.timeout.seconds:60}")
    private int timeoutSeconds;

    public boolean isConfigured() {
        return apiKey != null
                && !apiKey.isBlank()
                && !"OPENAI_KEY_NOT_SET".equals(apiKey);
    }

    public String getModel() {
        return model;
    }

    public ChatResult chat(String userPrompt) {
        return chat(userPrompt, 0.3);
    }

    public ChatResult chat(String userPrompt, double temperature) {
        if (!isConfigured()) {
            return ChatResult.fail("OPENAI_API_KEY 가 .env 에 없습니다.");
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            return ChatResult.fail("프롬프트가 비어 있습니다.");
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", userPrompt)));
            body.put("temperature", temperature);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String err = extractError(resp.body());
                log.warn("OpenAI HTTP {} model={} err={}", resp.statusCode(), model, err);
                return ChatResult.fail("HTTP " + resp.statusCode() + ": " + err);
            }

            String text = MAPPER.readTree(resp.body())
                    .path("choices").path(0)
                    .path("message").path("content").asText("").trim();
            if (text.isBlank()) {
                return ChatResult.fail("응답 본문이 비어 있습니다.");
            }
            Optional<Integer> tokens = extractTotalTokens(resp.body());
            log.info("OpenAI ok model={} tokens={}", model, tokens.orElse(null));
            return ChatResult.ok(text, tokens.orElse(null));
        } catch (Exception e) {
            log.warn("OpenAI exception: {}", e.toString());
            return ChatResult.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private static Optional<Integer> extractTotalTokens(String body) {
        try {
            int t = MAPPER.readTree(body).path("usage").path("total_tokens").asInt(-1);
            return t >= 0 ? Optional.of(t) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String extractError(String body) {
        if (body == null || body.isBlank()) return "(no body)";
        try {
            String msg = MAPPER.readTree(body).path("error").path("message").asText("").trim();
            if (!msg.isBlank()) return msg;
        } catch (Exception ignored) {
            // ignore
        }
        return body.length() > 300 ? body.substring(0, 300) + "…" : body;
    }

    public record ChatResult(boolean success, String text, String error, Integer totalTokens) {
        public static ChatResult ok(String text, Integer totalTokens) {
            return new ChatResult(true, text, null, totalTokens);
        }

        public static ChatResult fail(String error) {
            return new ChatResult(false, null, error, null);
        }
    }
}
