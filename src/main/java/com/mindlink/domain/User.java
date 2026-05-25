package com.mindlink.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 100)
    private String nickname;

    @Column(length = 100)
    private String region;

    @Column(name = "notification_enabled")
    private Boolean notificationEnabled = false;

    @Column(length = 20)
    private String phone;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (role == null) role = UserRole.USER;
        if (notificationEnabled == null) notificationEnabled = false;
    }

    public User() {}

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = UserRole.USER;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public UserRole getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getNickname() { return nickname; }
    public String getRegion() { return region; }
    public Boolean getNotificationEnabled() { return notificationEnabled; }
    public String getPhone() { return phone; }
    public String getProfileImageUrl() { return profileImageUrl; }

    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setRole(UserRole role) { this.role = role; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setRegion(String region) { this.region = region; }
    public void setNotificationEnabled(Boolean notificationEnabled) { this.notificationEnabled = notificationEnabled; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
}
