package com.mindlink.dto;

import com.mindlink.domain.PostComment;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class CommentResponse {

    private Long id;
    private Long postId;
    private String author;
    private String content;
    private LocalDateTime createdAt;
    private List<AttachmentResponse> attachments;

    public CommentResponse(PostComment comment) {
        this.id = comment.getId();
        this.postId = comment.getPost().getId();
        this.author = comment.getAuthor();
        this.content = comment.getContent();
        this.createdAt = comment.getCreatedAt();
        this.attachments = Collections.emptyList();
    }

    public Long getId() { return id; }
    public Long getPostId() { return postId; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<AttachmentResponse> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentResponse> attachments) { this.attachments = attachments; }
}
