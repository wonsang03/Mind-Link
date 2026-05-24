package com.mindlink.care;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 종합 보고서(위로 편지) 위저드 — 컨트롤러·서비스 사이 DTO 모음.
 */
public final class CareReportDtos {

    private CareReportDtos() {}

    /**
     * POST /api/care-reports/generate 요청 본문 — 위저드 한 번에 제출.
     * - mood / recentHardship / concern / smallComfort / hopeForward: 필수 (자유 입력)
     * - oneLineMessage: 선택
     * - assessments: 선택 — 완료한 검사만 (stress/depression/anxiety)
     *   예) { "stress": [0,2,3,1,...], "depression": [...], "anxiety": [...] }
     */
    public static class GenerateRequest {
        private String mood;
        private String recentHardship;
        private String concern;
        private String smallComfort;
        private String hopeForward;
        private String oneLineMessage;
        private Map<String, List<Integer>> assessments;

        public String getMood() { return mood; }
        public String getRecentHardship() { return recentHardship; }
        public String getConcern() { return concern; }
        public String getSmallComfort() { return smallComfort; }
        public String getHopeForward() { return hopeForward; }
        public String getOneLineMessage() { return oneLineMessage; }
        public Map<String, List<Integer>> getAssessments() { return assessments; }

        public void setMood(String mood) { this.mood = mood; }
        public void setRecentHardship(String recentHardship) { this.recentHardship = recentHardship; }
        public void setConcern(String concern) { this.concern = concern; }
        public void setSmallComfort(String smallComfort) { this.smallComfort = smallComfort; }
        public void setHopeForward(String hopeForward) { this.hopeForward = hopeForward; }
        public void setOneLineMessage(String oneLineMessage) { this.oneLineMessage = oneLineMessage; }
        public void setAssessments(Map<String, List<Integer>> assessments) { this.assessments = assessments; }
    }

    /** GET /api/care-reports — 내 목록의 한 줄 */
    public static class SummaryResponse {
        private final Long id;
        private final LocalDateTime createdAt;
        private final String excerpt;
        private final String riskLevel;
        private final List<String> themes;

        public SummaryResponse(Long id, LocalDateTime createdAt, String excerpt,
                               String riskLevel, List<String> themes) {
            this.id = id;
            this.createdAt = createdAt;
            this.excerpt = excerpt;
            this.riskLevel = riskLevel;
            this.themes = themes == null ? List.of() : themes;
        }

        public Long getId() { return id; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getExcerpt() { return excerpt; }
        public String getRiskLevel() { return riskLevel; }
        public List<String> getThemes() { return themes; }
    }

    /** GET /api/care-reports/{id} — 편지 상세 */
    public static class DetailResponse {
        private final Long id;
        private final LocalDateTime createdAt;
        private final String letterBody;
        private final String riskLevel;
        private final List<String> themes;

        public DetailResponse(Long id, LocalDateTime createdAt, String letterBody,
                              String riskLevel, List<String> themes) {
            this.id = id;
            this.createdAt = createdAt;
            this.letterBody = letterBody;
            this.riskLevel = riskLevel;
            this.themes = themes == null ? List.of() : themes;
        }

        public Long getId() { return id; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getLetterBody() { return letterBody; }
        public String getRiskLevel() { return riskLevel; }
        public List<String> getThemes() { return themes; }
    }

    /** POST /api/care-reports/generate 의 응답 — 신규 생성된 보고서 */
    public static class GenerateResponse {
        private final Long id;
        private final LocalDateTime createdAt;
        private final String letterBody;
        private final String riskLevel;
        private final List<String> themes;
        private final boolean usedFallback;

        public GenerateResponse(Long id, LocalDateTime createdAt, String letterBody,
                                String riskLevel, List<String> themes, boolean usedFallback) {
            this.id = id;
            this.createdAt = createdAt;
            this.letterBody = letterBody;
            this.riskLevel = riskLevel;
            this.themes = themes == null ? List.of() : themes;
            this.usedFallback = usedFallback;
        }

        public Long getId() { return id; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public String getLetterBody() { return letterBody; }
        public String getRiskLevel() { return riskLevel; }
        public List<String> getThemes() { return themes; }
        public boolean isUsedFallback() { return usedFallback; }
    }
}
