package com.mindlink.care;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gemini 를 호출해 AI 종합 보고서(위로 편지)를 한 번에 생성한다.
 */
@Component
public class CareLetterGeminiClient {

    private static final Logger log = LoggerFactory.getLogger(CareLetterGeminiClient.class);

    private static final String GEMINI_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final Pattern RETRY_SECONDS = Pattern.compile("retry in (\\d+(?:\\.\\d+)?)s", Pattern.CASE_INSENSITIVE);

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Value("${gemini.api.key:NOT_SET}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String primaryModel;

    @Value("${gemini.model.fallback:}")
    private String fallbackModelsCsv;

    public boolean isEnabled() {
        return apiKey != null && !"NOT_SET".equals(apiKey) && !apiKey.isBlank();
    }

    public CareLetterAiResult.GenerateResult generateLetter(String snapshotJson, CareReport.RiskLevel riskLevel) {
        if (!isEnabled()) {
            return CareLetterAiResult.GenerateResult.failure(CareLetterAiResult.FailureKind.DISABLED, null);
        }
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return CareLetterAiResult.GenerateResult.failure(CareLetterAiResult.FailureKind.EMPTY_INPUT, null);
        }

        String prompt = CareLetterPromptBuilder.build(snapshotJson, riskLevel);
        List<String> models = resolveModels();
        CareLetterAiResult.FailureKind lastKind = CareLetterAiResult.FailureKind.HTTP_ERROR;
        String lastHint = null;

        for (String model : models) {
            CallOutcome outcome = callWithRetry(model, prompt, 0.55);
            lastKind = outcome.kind();
            lastHint = outcome.userHint();

            if (outcome.text().isEmpty()) {
                log.warn("CareLetterGemini model={} failed kind={} hint={}",
                        model, outcome.kind(), outcome.userHint());
                if (outcome.kind() == CareLetterAiResult.FailureKind.QUOTA_EXCEEDED) {
                    break;
                }
                continue;
            }

            var draft = CareLetterPromptBuilder.parseResponse(outcome.text().get(), MAPPER);
            if (draft.isPresent()) {
                log.info("CareLetterGemini success model={} letterLen={} themes={}",
                        model, draft.get().letterBody().length(), draft.get().themes().size());
                return CareLetterAiResult.GenerateResult.success(draft.get());
            }
            lastKind = CareLetterAiResult.FailureKind.PARSE_ERROR;
            log.warn("CareLetterGemini model={} parse/short response", model);
        }

        return CareLetterAiResult.GenerateResult.failure(lastKind, lastHint);
    }

    private List<String> resolveModels() {
        List<String> models = new ArrayList<>();
        addModel(models, primaryModel);
        if (fallbackModelsCsv != null && !fallbackModelsCsv.isBlank()) {
            for (String part : fallbackModelsCsv.split(",")) {
                addModel(models, part.trim());
            }
        }
        if (models.isEmpty()) {
            models.add("gemini-2.0-flash");
        }
        return models;
    }

    private static void addModel(List<String> models, String model) {
        if (model == null || model.isBlank()) return;
        if (!models.contains(model)) models.add(model);
    }

    private CallOutcome callWithRetry(String model, String prompt, Double temperature) {
        CallOutcome first = callOnce(model, prompt, temperature);
        if (first.text().isPresent() || first.kind() != CareLetterAiResult.FailureKind.QUOTA_EXCEEDED) {
            return first;
        }
        long waitMs = parseRetryDelayMs(first.rawBody()).orElse(30_000L);
        waitMs = Math.min(waitMs, 35_000L);
        log.info("CareLetterGemini model={} quota retry after {}ms", model, waitMs);
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CallOutcome.fail(CareLetterAiResult.FailureKind.EXCEPTION, null, null);
        }
        return callOnce(model, prompt, temperature);
    }

    private CallOutcome callOnce(String model, String prompt, Double temperature) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            Map<String, Object> gen = new LinkedHashMap<>();
            if (temperature != null) gen.put("temperature", temperature);
            gen.put("responseMimeType", "application/json");
            body.put("generationConfig", gen);

            String bodyJson = MAPPER.writeValueAsString(body);
            String url = GEMINI_BASE + model + ":generateContent?key=" + apiKey;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                CareLetterAiResult.FailureKind kind = resp.statusCode() == 429
                        ? CareLetterAiResult.FailureKind.QUOTA_EXCEEDED
                        : CareLetterAiResult.FailureKind.HTTP_ERROR;
                String apiMsg = extractApiErrorMessage(resp.body());
                String userHint = kind == CareLetterAiResult.FailureKind.QUOTA_EXCEEDED
                        ? "Gemini API 무료/일일 한도에 도달했어요. 잠시 후 다시 시도하거나 Google AI Studio에서 할당량·결제를 확인해 주세요."
                        : null;
                log.warn("CareLetterGemini HTTP {} model={} apiError={}", resp.statusCode(), model, apiMsg);
                return CallOutcome.fail(kind, userHint, resp.body());
            }
            String text = MAPPER.readTree(resp.body())
                    .path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText("");
            if (text.isBlank()) {
                log.warn("CareLetterGemini empty candidate model={}", model);
                return CallOutcome.fail(CareLetterAiResult.FailureKind.HTTP_ERROR, null, resp.body());
            }
            return new CallOutcome(Optional.of(text.trim()), CareLetterAiResult.FailureKind.NONE, null, null);
        } catch (Exception e) {
            log.warn("CareLetterGemini exception model={}: {}", model, e.toString());
            return CallOutcome.fail(CareLetterAiResult.FailureKind.EXCEPTION, null, null);
        }
    }

    private static Optional<Long> parseRetryDelayMs(String body) {
        if (body == null || body.isBlank()) return Optional.empty();
        Matcher m = RETRY_SECONDS.matcher(body);
        if (m.find()) {
            try {
                double sec = Double.parseDouble(m.group(1));
                return Optional.of((long) (sec * 1000));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        try {
            JsonNode retry = MAPPER.readTree(body).path("error").path("details");
            if (retry.isArray()) {
                for (JsonNode d : retry) {
                    if ("type.googleapis.com/google.rpc.RetryInfo".equals(d.path("@type").asText())) {
                        String delay = d.path("retryDelay").asText("");
                        if (delay.endsWith("s")) {
                            double sec = Double.parseDouble(delay.substring(0, delay.length() - 1));
                            return Optional.of((long) (sec * 1000));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return Optional.empty();
    }

    private static String extractApiErrorMessage(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            return MAPPER.readTree(body).path("error").path("message").asText("").trim();
        } catch (Exception e) {
            return body.length() > 200 ? body.substring(0, 200) + "…" : body;
        }
    }

    private record CallOutcome(Optional<String> text, CareLetterAiResult.FailureKind kind, String userHint, String rawBody) {
        static CallOutcome fail(CareLetterAiResult.FailureKind kind, String userHint, String rawBody) {
            return new CallOutcome(Optional.empty(), kind, userHint, rawBody);
        }
    }
}
