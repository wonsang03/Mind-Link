package com.mindlink.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "book_reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_br_user_book",
                columnNames = {"user_id", "book_link"}))
public class BookReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "book_link", nullable = false, length = 500)
    private String bookLink;

    @Column(name = "book_title", length = 500)
    private String bookTitle;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId()                  { return id; }
    public User getUser()                { return user; }
    public String getBookLink()          { return bookLink; }
    public String getBookTitle()         { return bookTitle; }
    public int getRating()               { return rating; }
    public String getContent()           { return content; }
    public LocalDateTime getCreatedAt()  { return createdAt; }

    public void setUser(User user)             { this.user = user; }
    public void setBookLink(String bookLink)   { this.bookLink = bookLink; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public void setRating(int rating)          { this.rating = rating; }
    public void setContent(String content)     { this.content = content; }
}
