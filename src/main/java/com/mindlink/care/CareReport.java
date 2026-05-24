package com.mindlink.care;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * AI 종합 보고서(위로 편지) 저장 엔티티.
 * - userId 는 HttpSession 에서 받아 저장 (외래키는 application 레벨에서 검증)
 * - snapshotJson: CareContextAggregator 가 직렬화한 익명화된 사용자 스냅샷
 * - letterBody: Gemini 가 작성한 장문 위로 편지(또는 fallback 편지) 전문
 * - riskLevel: NORMAL / ELEVATED / CRISIS
 * - themes: 쉼표(,) 로 구분된 짧은 라벨 목록
 */
@Entity
@Table(name = "care_reports")
public class CareReport {

    public enum RiskLevel { NORMAL, ELEVATED, CRISIS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Lob
    @Column(name = "snapshot_json")
    private String snapshotJson;

    @Lob
    @Column(name = "letter_body", nullable = false)
    private String letterBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel = RiskLevel.NORMAL;

    @Column(name = "themes", length = 500)
    private String themes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (riskLevel == null) riskLevel = RiskLevel.NORMAL;
    }

    public CareReport() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getLetterBody() { return letterBody; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getThemes() { return themes; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public void setLetterBody(String letterBody) { this.letterBody = letterBody; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public void setThemes(String themes) { this.themes = themes; }
}
