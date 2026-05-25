package com.mindlink.recommendation.dto;

/** GeminiClient ↔ RecommendationService 내부 전송 DTO (1단계 분석 결과). */
public class GeminiAnalysisResult {
    public String emotion;      // 6개 태그 중 하나
    public String searchQuery;  // 네이버 검색용 키워드
    public String summary;      // 사용자 상태 한 줄 요약
}
