package com.mindlink.controller;

import com.mindlink.external.OpenAiClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OpenAI API 키·모델 연결 테스트용 (개발).
 * GET /api/test/openai?prompt=안녕
 */
@RestController
@RequestMapping("/api/test")
public class OpenAiTestController {

    private final OpenAiClient openAi;

    public OpenAiTestController(OpenAiClient openAi) {
        this.openAi = openAi;
    }

    @GetMapping("/openai")
    public Map<String, Object> testOpenAi(
            @RequestParam(defaultValue = "한 문장으로 'API 연결 성공'이라고만 답해줘.") String prompt) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", openAi.isConfigured());
        out.put("model", openAi.getModel());

        if (!openAi.isConfigured()) {
            out.put("ok", false);
            out.put("error", ".env 에 OPENAI_API_KEY=sk-... 를 넣고 서버를 재시작하세요.");
            return out;
        }

        OpenAiClient.ChatResult result = openAi.chat(prompt);
        out.put("ok", result.success());
        if (result.success()) {
            out.put("reply", result.text());
            if (result.totalTokens() != null) {
                out.put("totalTokens", result.totalTokens());
            }
        } else {
            out.put("error", result.error());
        }
        return out;
    }
}
