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

    @Column(nullable = false, length = 50)
    private String author;

    @Column(nullable = false, columnDefinition = "TEXT")
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

    public Long getId() { return id; }
    public Post getPost() { return post; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setPost(Post post) { this.post = post; }
    public void setAuthor(String author) { this.author = author; }
    public void setContent(String content) { this.content = content; }
}
