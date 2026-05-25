package com.mindlink.recommendation.dto;

import lombok.Getter;
import lombok.Setter;

/** POST /api/recommendations/ai 요청 바디. */
@Getter @Setter
public class AiRecommendationRequest {
    private String message;
}
