package com.mindlink.chatcluster;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 사용자/페르소나의 3축 점수(스트레스·우울·불안) + 정규화 + K-Means 결과 cluster_id.
 * - userId 가 NULL 이면 합성(페르소나) 데이터, 아니면 실제 users.id
 * - 원점수: stress 0~40, depression 0~27, anxiety 0~21 (DDL 의 CHECK 제약과 동일)
 * - 정규화: 각 점수를 만점으로 나눠 0~1 로 둔다 — K-Means 입력
 */
@Entity
@Table(name = "user_assessment_profiles")
public class UserAssessmentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "persona_key", nullable = false, length = 40)
    private String personaKey;

    @Column(name = "persona_label", nullable = false, length = 80)
    private String personaLabel;

    @Column(name = "persona_story", length = 500)
    private String personaStory;

    @Column(name = "stress_score", nullable = false)
    private Double stressScore;

    @Column(name = "depression_score", nullable = false)
    private Double depressionScore;

    @Column(name = "anxiety_score", nullable = false)
    private Double anxietyScore;

    @Column(name = "stress_norm", nullable = false)
    private Double stressNorm;

    @Column(name = "depression_norm", nullable = false)
    private Double depressionNorm;

    @Column(name = "anxiety_norm", nullable = false)
    private Double anxietyNorm;

    @Column(name = "cluster_id")
    private Integer clusterId;

    @Column(name = "is_synthetic", nullable = false)
    private Integer isSynthetic = 1;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (isSynthetic == null) isSynthetic = 1;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public UserAssessmentProfile() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getPersonaKey() { return personaKey; }
    public String getPersonaLabel() { return personaLabel; }
    public String getPersonaStory() { return personaStory; }
    public Double getStressScore() { return stressScore; }
    public Double getDepressionScore() { return depressionScore; }
    public Double getAnxietyScore() { return anxietyScore; }
    public Double getStressNorm() { return stressNorm; }
    public Double getDepressionNorm() { return depressionNorm; }
    public Double getAnxietyNorm() { return anxietyNorm; }
    public Integer getClusterId() { return clusterId; }
    public Integer getIsSynthetic() { return isSynthetic; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /** 합성 페르소나 = user_id 없음. 실사용자는 users.id 와 연결됨. */
    public boolean isSynthetic() { return userId == null; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setPersonaKey(String personaKey) { this.personaKey = personaKey; }
    public void setPersonaLabel(String personaLabel) { this.personaLabel = personaLabel; }
    public void setPersonaStory(String personaStory) { this.personaStory = personaStory; }
    public void setStressScore(Double stressScore) { this.stressScore = stressScore; }
    public void setDepressionScore(Double depressionScore) { this.depressionScore = depressionScore; }
    public void setAnxietyScore(Double anxietyScore) { this.anxietyScore = anxietyScore; }
    public void setStressNorm(Double stressNorm) { this.stressNorm = stressNorm; }
    public void setDepressionNorm(Double depressionNorm) { this.depressionNorm = depressionNorm; }
    public void setAnxietyNorm(Double anxietyNorm) { this.anxietyNorm = anxietyNorm; }
    public void setClusterId(Integer clusterId) { this.clusterId = clusterId; }
    public void setIsSynthetic(Integer isSynthetic) { this.isSynthetic = isSynthetic; }
}
