package com.mindlink.dto;

import com.mindlink.domain.Post;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class PostResponse {

    private Long id;
    private String author;
    private String title;
    private String content;
    private String category;
    private int likes;
    private LocalDateTime createdAt;
    private int commentCount;
    private List<AttachmentResponse> attachments;

    public PostResponse(Post post) {
        this.id = post.getId();
        this.author = post.getAuthor();
        this.title = post.getTitle();
        this.content = post.getContent();
        this.category = post.getCategory();
        this.likes = post.getLikes();
        this.createdAt = post.getCreatedAt();
        this.commentCount = post.getComments().size();
        this.attachments = Collections.emptyList();
    }

    public Long getId() { return id; }
    public String getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public int getLikes() { return likes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public int getCommentCount() { return commentCount; }
    public List<AttachmentResponse> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentResponse> attachments) { this.attachments = attachments; }
}
