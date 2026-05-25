package com.mindlink.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_comments")
public class PostComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private PostComment parentComment;

    @Column(nullable = false, length = 200)
    private String author;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public PostComment() {}

    public PostComment(Post post, String author, String content) {
        this.post = post;
        this.author = author;
        this.content = content;
    }

    public PostComment(Post post, PostComment parent, String author, String content) {
        this.post = post;
        this.parentComment = parent;
        this.author = author;
        this.content = content;
    }

    public Long getId() { return id; }
    public Post getPost() { return post; }
    public PostComment getParentComment() { return parentComment; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setPost(Post post) { this.post = post; }
    public void setParentComment(PostComment parentComment) { this.parentComment = parentComment; }
    public void setAuthor(String author) { this.author = author; }
    public void setContent(String content) { this.content = content; }
}
