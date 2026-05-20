package com.mindlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PostRequest {

    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = 200)
    private String title;

    @NotBlank(message = "내용을 입력해주세요.")
    private String content;

    @NotBlank(message = "카테고리를 선택해주세요.")
    private String category;

    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setCategory(String category) { this.category = category; }
}
