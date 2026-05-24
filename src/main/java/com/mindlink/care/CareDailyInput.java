package com.mindlink.care;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 보고서 생성 시 사용자가 직접 적어 넣은 일일 입력값. (선택)
 * - 같은 날 여러 번 생성 가능 — 매 보고서마다 1행 저장
 */
@Entity
@Table(name = "care_daily_inputs")
public class CareDailyInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "input_date", nullable = false)
    private LocalDate inputDate;

    @Column(name = "mood", length = 500)
    private String mood;

    @Column(name = "hardship", length = 1000)
    private String hardship;

    @Column(name = "current_thought", length = 1000)
    private String currentThought;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (inputDate == null) inputDate = LocalDate.now();
    }

    public CareDailyInput() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public LocalDate getInputDate() { return inputDate; }
    public String getMood() { return mood; }
    public String getHardship() { return hardship; }
    public String getCurrentThought() { return currentThought; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setUserId(Long userId) { this.userId = userId; }
    public void setInputDate(LocalDate inputDate) { this.inputDate = inputDate; }
    public void setMood(String mood) { this.mood = mood; }
    public void setHardship(String hardship) { this.hardship = hardship; }
    public void setCurrentThought(String currentThought) { this.currentThought = currentThought; }
}
