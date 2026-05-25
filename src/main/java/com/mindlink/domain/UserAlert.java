package com.mindlink.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_alerts")
@org.hibernate.annotations.DynamicInsert
public class UserAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "alert_type", nullable = false, length = 30)
    private String alertType;   // DETERIORATION | HIGH_RISK

    @Column(columnDefinition = "CLOB")
    private String message;

    /** 관리자 알림 제목 등 (선택) */
    @Column(length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_result_id")
    private AssessmentResult assessmentResult;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "related_post_id")
    private Long relatedPostId;

    @Column(name = "related_comment_id")
    private Long relatedCommentId;

    @Column(name = "notice_id")
    private Long noticeId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getAlertType() { return alertType; }
    public String getMessage() { return message; }
    public String getTitle() { return title; }
    public AssessmentResult getAssessmentResult() { return assessmentResult; }
    public String getLinkUrl() { return linkUrl; }
    public Long getRelatedPostId() { return relatedPostId; }
    public Long getRelatedCommentId() { return relatedCommentId; }
    public Long getNoticeId() { return noticeId; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setUser(User user) { this.user = user; }
    public void setAlertType(String alertType) { this.alertType = alertType; }
    public void setMessage(String message) { this.message = message; }
    public void setTitle(String title) { this.title = title; }
    public void setAssessmentResult(AssessmentResult assessmentResult) { this.assessmentResult = assessmentResult; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    public void setRelatedPostId(Long relatedPostId) { this.relatedPostId = relatedPostId; }
    public void setRelatedCommentId(Long relatedCommentId) { this.relatedCommentId = relatedCommentId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }
    public void setRead(boolean read) { this.read = read; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
