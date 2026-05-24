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
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "type_key", nullable = false, length = 50)
    private String typeKey;

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Column(nullable = false)
    private int score;

    @Column(name = "result_level", nullable = false, length = 60)
    private String level;

    @Column(name = "high_risk", nullable = false)
    private boolean highRisk;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public AssessmentResult() {}

    public AssessmentResult(User user, String typeKey, String typeName,
                            int score, String level, boolean highRisk) {
        this.user = user;
        this.typeKey = typeKey;
        this.typeName = typeName;
        this.score = score;
        this.level = level;
        this.highRisk = highRisk;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTypeKey() { return typeKey; }
    public String getTypeName() { return typeName; }
    public int getScore() { return score; }
    public String getLevel() { return level; }
    public boolean isHighRisk() { return highRisk; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
