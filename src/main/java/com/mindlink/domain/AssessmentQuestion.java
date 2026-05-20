package com.mindlink.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "assessment_questions")
public class AssessmentQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_type_id", nullable = false)
    private AssessmentType assessmentType;

    @Column(name = "order_num", nullable = false)
    private int orderNum;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean reversed = false;

    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 1")
    private int part = 1;

    public AssessmentQuestion() {}

    public Long getId() { return id; }
    public AssessmentType getAssessmentType() { return assessmentType; }
    public int getOrderNum() { return orderNum; }
    public String getContent() { return content; }
    public boolean isReversed() { return reversed; }
    public int getPart() { return part; }
}
