package com.mindlink.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "score_ranges")
public class ScoreRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_type_id", nullable = false)
    private AssessmentType assessmentType;

    @Column(name = "min_score", nullable = false)
    private int minScore;

    @Column(name = "max_score", nullable = false)
    private int maxScore;

    @Column(name = "result_level", nullable = false, length = 30)
    private String level;

    @Column(columnDefinition = "TEXT")
    private String message;

    public ScoreRange() {}

    public Long getId() { return id; }
    public AssessmentType getAssessmentType() { return assessmentType; }
    public int getMinScore() { return minScore; }
    public int getMaxScore() { return maxScore; }
    public String getLevel() { return level; }
    public String getMessage() { return message; }
}
