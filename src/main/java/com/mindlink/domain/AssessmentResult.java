package com.mindlink.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "assessment_results")
public class AssessmentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "type_key", nullable = false, length = 50)
    private String typeKey;

    /** Hibernate 구테이블 NOT NULL 호환 */
    @Column(name = "type_name", length = 100)
    private String typeName;

    private Integer score;

    @Column(name = "score_level", length = 30)
    private String level;

    /** Hibernate 구테이블 NOT NULL 호환 (score_level 과 동일 값 유지) */
    @Column(name = "result_level", length = 30)
    private String resultLevel;

    @Column(name = "is_high_risk", nullable = false)
    private boolean highRisk;

    @Column(name = "personal_score")
    private Integer personalScore;

    @Column(name = "personal_level", length = 30)
    private String personalLevel;

    @Column(name = "work_score")
    private Integer workScore;

    @Column(name = "work_level", length = 30)
    private String workLevel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime completedAt;

    @PrePersist
    void beforeInsert() {
        if (completedAt == null) {
            completedAt = LocalDateTime.now();
        }
        if (typeName == null || typeName.isBlank()) {
            typeName = defaultTypeName(typeKey);
        }
        String effective = effectiveLevel();
        if (level == null || level.isBlank()) {
            level = effective;
        }
        if (resultLevel == null || resultLevel.isBlank()) {
            resultLevel = effective;
        }
    }

    private static String defaultTypeName(String typeKey) {
        if (typeKey == null) return "자가진단";
        return switch (typeKey) {
            case "depression" -> "우울증 (PHQ-9)";
            case "anxiety"    -> "불안장애 (GAD-7)";
            case "stress"     -> "스트레스 (PSS-10)";
            case "burnout"    -> "번아웃 (CBI)";
            default           -> typeKey;
        };
    }

    /** 번아웃은 personal/work 중 더 높은 단계, 그 외는 score_level */
    private String effectiveLevel() {
        if ("burnout".equals(typeKey)) {
            return worseBurnoutLevel(personalLevel, workLevel);
        }
        if (level != null && !level.isBlank()) return level;
        return "미분류";
    }

    private static String worseBurnoutLevel(String a, String b) {
        int oa = burnoutOrder(a);
        int ob = burnoutOrder(b);
        if (oa >= ob) return a != null ? a : (b != null ? b : "미분류");
        return b != null ? b : (a != null ? a : "미분류");
    }

    private static int burnoutOrder(String level) {
        if (level == null) return -1;
        return switch (level) {
            case "낮음" -> 0;
            case "보통" -> 1;
            case "높음" -> 2;
            default -> -1;
        };
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTypeKey() { return typeKey; }
    public String getTypeName() { return typeName; }
    public Integer getScore() { return score; }
    public String getLevel() { return level; }
    public String getResultLevel() { return resultLevel; }
    public boolean isHighRisk() { return highRisk; }
    public Integer getPersonalScore() { return personalScore; }
    public String getPersonalLevel() { return personalLevel; }
    public Integer getWorkScore() { return workScore; }
    public String getWorkLevel() { return workLevel; }
    public LocalDateTime getCompletedAt() { return completedAt; }

    public void setUser(User user) { this.user = user; }
    public void setTypeKey(String typeKey) { this.typeKey = typeKey; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    public void setScore(Integer score) { this.score = score; }
    public void setLevel(String level) { this.level = level; }
    public void setResultLevel(String resultLevel) { this.resultLevel = resultLevel; }
    public void setHighRisk(boolean highRisk) { this.highRisk = highRisk; }
    public void setPersonalScore(Integer personalScore) { this.personalScore = personalScore; }
    public void setPersonalLevel(String personalLevel) { this.personalLevel = personalLevel; }
    public void setWorkScore(Integer workScore) { this.workScore = workScore; }
    public void setWorkLevel(String workLevel) { this.workLevel = workLevel; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
