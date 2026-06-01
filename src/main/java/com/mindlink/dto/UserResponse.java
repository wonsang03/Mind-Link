package com.mindlink.dto;

import com.mindlink.domain.User;
import com.mindlink.domain.UserRole;

import java.time.LocalDateTime;

public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private UserRole role;
    private LocalDateTime createdAt;
    private String nickname;
    private String region;
    private Boolean notificationEnabled;
    private String phone;
    private String profileImageUrl;
    private boolean sensitiveDataConsent;

    public UserResponse(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.role = user.getRole();
        this.createdAt = user.getCreatedAt();
        this.nickname = user.getNickname();
        this.region = user.getRegion();
        this.notificationEnabled = user.getNotificationEnabled();
        this.phone = user.getPhone();
        this.profileImageUrl = user.getProfileImageUrl();
        this.sensitiveDataConsent = user.isSensitiveDataConsent();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public UserRole getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getNickname() { return nickname; }
    public String getRegion() { return region; }
    public Boolean getNotificationEnabled() { return notificationEnabled; }
    public String getPhone() { return phone; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public boolean isSensitiveDataConsent() { return sensitiveDataConsent; }
}
