package com.mindlink.recommendation.dto;

import lombok.Getter;

import java.util.List;

/**
 * 최종 응답 JSON (엄격한 구조).
 * { emotion, emotions, reason, books, count, source, personalized }
 */
@Getter
public class RecommendationResponse {
    private final String emotion;        // 주 감정 태그 (DEPRESSION 등)
    private final List<String> emotions; // 복합 감정 목록 (프론트 배지용, optional)
    private final String reason;
    private final List<BookDto> books;
    private final int count;
    private final String source;         // "DB" | "DB+AI" | "EMPTY"
    private final boolean personalized;
    private boolean crisis = false;      // 자해·위기 신호 감지 시 true — 프론트에서 지원 안내 우선 노출

    public void setCrisis(boolean crisis) { this.crisis = crisis; }

    public RecommendationResponse(String emotion, String reason, List<BookDto> books, String source) {
        this(emotion, reason, books, source, false, List.of());
    }

    public RecommendationResponse(String emotion, String reason, List<BookDto> books, String source, boolean personalized) {
        this(emotion, reason, books, source, personalized, List.of());
    }

    public RecommendationResponse(String emotion, String reason, List<BookDto> books, String source,
                           boolean personalized, List<String> emotions) {
        this.emotion = emotion;
        this.emotions = emotions != null ? emotions : List.of();
        this.reason  = reason != null ? reason : "";
        this.books   = books  != null ? books  : List.of();
        this.count   = this.books.size();
        this.source  = source != null ? source : "EMPTY";
        this.personalized = personalized;
    }
}
