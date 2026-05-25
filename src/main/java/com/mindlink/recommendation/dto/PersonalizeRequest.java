package com.mindlink.recommendation.dto;

import lombok.Getter;
import lombok.Setter;

/** POST /api/recommendations/personalize 요청 바디. */
@Getter @Setter
public class PersonalizeRequest {
    private String message;
    private String emotion; // optional — 명시적 감정 태그
}
