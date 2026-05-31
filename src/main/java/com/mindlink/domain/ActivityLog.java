package com.mindlink.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 추천 활동(호흡·감사 일기·감정 체크인 등) 1회 수행 기록.
 * 활동 일지·연속일·여정의 공통 기반.
 */
@Entity
@Table(name = "activity_log")
@org.hibernate.annotations.DynamicInsert
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 모달의 data-activity 값 (gratitude / breathing / checkin ...) */
    @Column(name = "activity_key", nullable = false, length = 40)
    private String activityKey;

    /** 활동별 입력/결과 JSON (감사 일기 항목, 체크인 기분 등). 없으면 null */
    @Column(columnDefinition = "CLOB")
    private String payload;

    /** 감정 체크인 정서가(1~5). 추이 그래프용. 그 외 활동은 null */
    @Column(name = "mood_score")
    private Integer moodScore;

    /** 소요 시간(초, 선택) */
    @Column(name = "duration_sec")
    private Integer durationSec;

    /** 여정(프로그램) 진행 중 수행했다면 그 키 (선택) */
    @Column(name = "program_key", length = 60)
    private String programKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId()               { return id; }
    public User getUser()             { return user; }
    public String getActivityKey()    { return activityKey; }
    public String getPayload()        { return payload; }
    public Integer getMoodScore()     { return moodScore; }
    public Integer getDurationSec()   { return durationSec; }
    public String getProgramKey()     { return programKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setUser(User user)                 { this.user = user; }
    public void setActivityKey(String activityKey) { this.activityKey = activityKey; }
    public void setPayload(String payload)         { this.payload = payload; }
    public void setMoodScore(Integer moodScore)    { this.moodScore = moodScore; }
    public void setDurationSec(Integer durationSec) { this.durationSec = durationSec; }
    public void setProgramKey(String programKey)   { this.programKey = programKey; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
