package com.mindlink.care;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindlink.external.OpenAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenAI(Chat Completions)로 AI 종합 보고서 위로 편지를 생성한다.
 * .env 의 OPENAI_API_KEY, openai.model(gpt-4o-mini 등) 사용.
 */
@Component
public class CareLetterOpenAiClient {

    private static final Logger log = LoggerFactory.getLogger(CareLetterOpenAiClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final OpenAiClient openAi;

    public CareLetterOpenAiClient(OpenAiClient openAi) {
        this.openAi = openAi;
    }

    public boolean isEnabled() {
        return openAi.isConfigured();
    }

    public CareLetterAiResult.GenerateResult generateLetter(String snapshotJson, CareReport.RiskLevel riskLevel) {
        if (!isEnabled()) {
            return CareLetterAiResult.GenerateResult.failure(
                    CareLetterAiResult.FailureKind.DISABLED,
                    "OPENAI_API_KEY 가 .env 에 없습니다.");
        }
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return CareLetterAiResult.GenerateResult.failure(CareLetterAiResult.FailureKind.EMPTY_INPUT, null);
        }

        String prompt = CareLetterPromptBuilder.build(snapshotJson, riskLevel);
        OpenAiClient.ChatResult chat = openAi.chat(prompt, 0.55);

        if (!chat.success()) {
            String err = chat.error() != null ? chat.error() : "알 수 없는 오류";
            CareLetterAiResult.FailureKind kind = err.contains("429") || err.toLowerCase().contains("quota")
                    ? CareLetterAiResult.FailureKind.QUOTA_EXCEEDED
                    : CareLetterAiResult.FailureKind.HTTP_ERROR;
            String hint = kind == CareLetterAiResult.FailureKind.QUOTA_EXCEEDED
                    ? "OpenAI API 한도 또는 잔액 문제가 있어요. platform.openai.com 에서 확인해 주세요."
                    : "OpenAI API 오류: " + err;
            log.warn("CareLetterOpenAI failed model={} err={}", openAi.getModel(), err);
            return CareLetterAiResult.GenerateResult.failure(kind, hint);
        }

        var draft = CareLetterPromptBuilder.parseResponse(chat.text(), MAPPER);
        if (draft.isPresent()) {
            log.info("CareLetterOpenAI success model={} letterLen={} tokens={}",
                    openAi.getModel(), draft.get().letterBody().length(), chat.totalTokens());
            return CareLetterAiResult.GenerateResult.success(draft.get());
        }

        log.warn("CareLetterOpenAI parse/short model={}", openAi.getModel());
        return CareLetterAiResult.GenerateResult.failure(
                CareLetterAiResult.FailureKind.PARSE_ERROR,
                "OpenAI 응답을 JSON 편지 형식으로 해석하지 못했어요.");
    }
}
