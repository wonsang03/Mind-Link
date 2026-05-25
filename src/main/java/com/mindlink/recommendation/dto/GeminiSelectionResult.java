package com.mindlink.recommendation.dto;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GeminiClient ↔ RecommendationService 내부 전송 DTO (3단계 선별 결과). */
public class GeminiSelectionResult {
    public String reason;                 // 사용자에게 보여줄 추천 이유
    public List<Integer> selectedIndices; // 후보 목록 내 선택된 0-based 인덱스
    /** 후보 인덱스 → Gemini가 판정한 권별 감정 태그(영문) */
    public Map<Integer, String> bookEmotionsByIndex = new LinkedHashMap<>();
}
