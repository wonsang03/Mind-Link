package com.mindlink.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OpenAI API 호출 클라이언트.
 * API Key 설정 후 실제 호출 로직을 구현하세요.
 *
 * application.properties 또는 환경변수 설정:
 *   openai.api.key=sk-xxxx
 *   openai.api.url=https://api.openai.com/v1/chat/completions
 *   openai.model=gpt-4o-mini
 */
@Component
public class OpenAiClient {

    @Value("${openai.api.key:OPENAI_KEY_NOT_SET}")
    private String apiKey;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    /**
     * AI에게 프롬프트를 전달하고 응답 텍스트를 반환합니다.
     * TODO: RestClient 또는 WebClient 를 이용해 실제 API 호출을 구현하세요.
     */
    public String call(String prompt) {
        if ("OPENAI_KEY_NOT_SET".equals(apiKey)) {
            return "[AI 응답 미설정] openai.api.key 를 application.properties 에 추가해주세요.";
        }
        // TODO: 실제 API 호출 구현
        // RestClient client = RestClient.create();
        // ...
        throw new UnsupportedOperationException("OpenAI API 호출이 아직 구현되지 않았습니다.");
    }
}
