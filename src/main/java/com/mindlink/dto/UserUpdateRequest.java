package com.mindlink.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UserUpdateRequest {

    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 50, message = "이름은 50자 이내로 입력해주세요.")
    private String name;

    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @Size(max = 100, message = "닉네임은 100자 이내로 입력해주세요.")
    private String nickname;

    @Size(max = 100, message = "거주 지역은 100자 이내로 입력해주세요.")
    private String region;

    private Boolean notificationEnabled;

    @Size(max = 20, message = "전화번호는 20자 이내로 입력해주세요.")
    private String phone;

    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
    public String getRegion() { return region; }
    public Boolean getNotificationEnabled() { return notificationEnabled != null ? notificationEnabled : false; }
    public String getPhone() { return phone; }

    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setRegion(String region) { this.region = region; }
    public void setNotificationEnabled(Boolean notificationEnabled) { this.notificationEnabled = notificationEnabled; }
    public void setPhone(String phone) { this.phone = phone; }
}
