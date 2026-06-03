package com.mindlink.chatcluster;

import java.util.List;

/** 3D 클러스터링 REST 응답 DTO. */
public final class ClusterDtos {

    private ClusterDtos() {}

    /** GET /api/chat-cluster/visualization 응답. */
    public record VisualizationResponse(
            int k,
            Axes axes,
            Weights weights,
            List<Point> points,
            List<Centroid> centroids,
            Stats stats
    ) {}

    /** 축 표시명 (정규화 분모 — UI 가 툴팁에 0~1 ↔ 원점수 환산할 때 사용). */
    public record Axes(String xAxis, String yAxis, String zAxis,
                       int xMax, int yMax, int zMax) {}

    /** K-Means 가중치 (UI 우측 패널 표시용). */
    public record Weights(double stress, double depression, double anxiety) {}

    /** 시각화 한 점. */
    public record Point(
            Long id,
            String personaKey,
            String personaLabel,
            Double stressNorm,
            Double depressionNorm,
            Double anxietyNorm,
            Double stressScore,
            Double depressionScore,
            Double anxietyScore,
            Integer clusterId,
            boolean synthetic,
            boolean isMe
    ) {}

    /** 집단 중심점. */
    public record Centroid(
            int clusterId,
            int memberCount,
            double centroidS,
            double centroidD,
            double centroidA
    ) {}

    /** 클러스터링 메타데이터. */
    public record Stats(
            int totalPoints,
            int syntheticCount,
            int realCount,
            double silhouette,
            int iterations,
            long elapsedMillis
    ) {}

    /** GET /api/chat-cluster/my 응답. */
    public record MyClusterResponse(
            boolean hasProfile,
            Point me,
            int clusterId,
            int clusterMemberCount,
            List<String> samePersonaLabels,
            String clusterLabel,        // 동적 정서 유형명 (예: "스트레스 우세형") — 군집 평균좌표 기반
            String clusterDescription,  // 유형 설명 1~2문장
            String summary
    ) {}

    /** POST /api/chat-cluster/profile 요청. */
    public record SaveProfileRequest(
            Double stressScore,
            Double depressionScore,
            Double anxietyScore
    ) {}

    /** GET /api/chat-cluster/stats/real 응답 항목 — 실사용자만(페르소나 제외) 군집 통계. */
    public record RealClusterStat(
            int clusterId,
            long memberCount,
            double avgStressNorm,
            double avgDepressionNorm,
            double avgAnxietyNorm,
            String label
    ) {}

    /** POST /api/chat-cluster/recompute 응답. */
    public record RecomputeResponse(
            int k,
            int totalPoints,
            double silhouette,
            int iterations,
            long elapsedMillis
    ) {}

    /** GET /api/chat-cluster/search 응답 — 실사용자 이름 검색. */
    public record SearchResponse(
            String query,
            int count,
            List<SearchMatch> matches
    ) {}

    public record SearchMatch(
            Long profileId,
            Long userId,
            String name,
            String email,
            Double stressScore,
            Double depressionScore,
            Double anxietyScore,
            Double stressNorm,
            Double depressionNorm,
            Double anxietyNorm,
            Integer clusterId
    ) {}
}
