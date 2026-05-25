package com.mindlink.recommendation.dto;

import com.mindlink.recommendation.EmotionCategory;

/** 감정별 추천 권 수 슬롯 (slots 합 = 3). */
public record EmotionSlot(EmotionCategory emotion, int count) {}
