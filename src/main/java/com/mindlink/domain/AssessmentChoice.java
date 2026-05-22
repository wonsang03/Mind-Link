package com.mindlink.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "assessment_choices")
public class AssessmentChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_type_id", nullable = false)
    private AssessmentType assessmentType;

    @Column(name = "order_num", nullable = false)
    private int orderNum;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false)
    private int score;

    public AssessmentChoice() {}

    public Long getId() { return id; }
    public AssessmentType getAssessmentType() { return assessmentType; }
    public int getOrderNum() { return orderNum; }
    public String getLabel() { return label; }
    public int getScore() { return score; }
}
