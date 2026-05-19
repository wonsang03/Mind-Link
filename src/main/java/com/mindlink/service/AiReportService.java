package com.mindlink.service;

import com.mindlink.dto.AiReportResponse;
import com.mindlink.dto.DiagnosisResultResponse;
import org.springframework.stereotype.Service;

/**
 * 자가진단 결과를 바탕으로 AI API를 호출하여 추천 메시지를 생성합니다.
 * 현재는 API Key 미설정 상태로 더미 응답을 반환합니다.
 * 실제 연결 시 OpenAiClient 를 주입하여 사용하세요.
 */
@Service
public class AiReportService {

    // private final OpenAiClient openAiClient;
    // API Key 설정 후 주석 해제하여 사용하세요.

    public AiReportResponse generateReport(DiagnosisResultResponse result) {
        // TODO: OpenAiClient 를 통해 실제 AI API 호출
        // String prompt = buildPrompt(result);
        // String rawResponse = openAiClient.call(prompt);
        // return parseResponse(rawResponse);

        // API 연결 전 더미 응답
        String summary = String.format("[%s] 진단 결과: %s (점수: %d)",
                result.getTestType(), result.getLevel(), result.getScore());
        String recommendation = result.isHighRisk()
                ? "전문 상담사와의 상담을 권장합니다. 상담소 찾기 메뉴를 이용해보세요."
                : "일상적인 스트레스 관리와 규칙적인 생활습관을 유지해보세요.";
        return new AiReportResponse(summary, recommendation, null);
    }

    private String buildPrompt(DiagnosisResultResponse result) {
        return String.format(
                "사용자가 %s 자가진단을 받았습니다. 점수: %d, 등급: %s. 적절한 조언을 한국어로 제공해주세요.",
                result.getTestType(), result.getScore(), result.getLevel()
        );
    }
}
