package com.mindlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NoticeRequest {

    @NotBlank(message = "카테고리를 입력해주세요.")
    @Size(max = 30, message = "카테고리는 30자 이내로 입력해주세요.")
    private String category;

    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = 200, message = "제목은 200자 이내로 입력해주세요.")
    private String title;

    @NotBlank(message = "요약을 입력해주세요.")
    @Size(max = 500, message = "요약은 500자 이내로 입력해주세요.")
    private String summary;

    @NotBlank(message = "내용을 입력해주세요.")
    private String content;

    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getContent() { return content; }

    public void setCategory(String category) { this.category = category; }
    public void setTitle(String title) { this.title = title; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setContent(String content) { this.content = content; }
}
