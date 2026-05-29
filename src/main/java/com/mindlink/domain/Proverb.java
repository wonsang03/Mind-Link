package com.mindlink.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "proverbs")
public class Proverb {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String content;

    @Column(length = 150)
    private String source;

    @Column(nullable = false, length = 30)
    private String page;

    @Column(name = "sort_order")
    private int sortOrder;

    public Long getId() { return id; }
    public String getContent() { return content; }
    public String getSource() { return source; }
    public String getPage() { return page; }
    public int getSortOrder() { return sortOrder; }
}
