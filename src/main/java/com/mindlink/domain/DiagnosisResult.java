package com.mindlink.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "diagnosis_results")
public class DiagnosisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // 비로그인 사용자는 null 허용

    @Column(nullable = false, length = 30)
    private String testType; // depression, anxiety, stress, burnout

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, length = 20)
    private String level; // 정상, 경미, 중등도, 심각

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public DiagnosisResult() {}

    public DiagnosisResult(User user, String testType, int score, String level) {
        this.user = user;
        this.testType = testType;
        this.score = score;
        this.level = level;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTestType() { return testType; }
    public int getScore() { return score; }
    public String getLevel() { return level; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setUser(User user) { this.user = user; }
    public void setTestType(String testType) { this.testType = testType; }
    public void setScore(int score) { this.score = score; }
    public void setLevel(String level) { this.level = level; }
}
