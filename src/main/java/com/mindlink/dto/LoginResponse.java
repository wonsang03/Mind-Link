package com.mindlink.dto;

import com.mindlink.domain.UserRole;

public class LoginResponse {

    private Long userId;
    private String name;
    private String email;
    private UserRole role;

    public LoginResponse(Long userId, String name, String email, UserRole role) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public UserRole getRole() { return role; }
}
