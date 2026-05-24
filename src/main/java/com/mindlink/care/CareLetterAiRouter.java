package com.mindlink.care;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * .env 의 CARE_LLM_PROVIDER 로 AI 종합 보고서 백엔드 선택.
 * openai (기본) | gemini
 */
@Component
public class CareLetterAiRouter {

    @Value("${care.llm.provider:openai}")
    private String provider;

    private final CareLetterOpenAiClient openAi;
    private final CareLetterGeminiClient gemini;

    public CareLetterAiRouter(CareLetterOpenAiClient openAi, CareLetterGeminiClient gemini) {
        this.openAi = openAi;
        this.gemini = gemini;
    }

    public boolean isEnabled() {
        return useOpenAi() ? openAi.isEnabled() : gemini.isEnabled();
    }

    public String activeProvider() {
        return useOpenAi() ? "openai" : "gemini";
    }

    public CareLetterAiResult.GenerateResult generateLetter(String snapshotJson, CareReport.RiskLevel riskLevel) {
        if (useOpenAi()) {
            return openAi.generateLetter(snapshotJson, riskLevel);
        }
        return gemini.generateLetter(snapshotJson, riskLevel);
    }

    private boolean useOpenAi() {
        return provider == null
                || provider.isBlank()
                || provider.equalsIgnoreCase("openai");
    }
}
