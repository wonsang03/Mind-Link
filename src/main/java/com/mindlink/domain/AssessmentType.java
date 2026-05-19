package com.mindlink.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assessment_types")
public class AssessmentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(name = "type_key", nullable = false, unique = true, length = 50)
    private String typeKey;

    @Column(length = 30)
    private String duration;

    @OneToMany(mappedBy = "assessmentType", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderNum ASC")
    private List<AssessmentQuestion> questions = new ArrayList<>();

    @OneToMany(mappedBy = "assessmentType", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderNum ASC")
    private List<AssessmentChoice> choices = new ArrayList<>();

    @OneToMany(mappedBy = "assessmentType", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("minScore ASC")
    private List<ScoreRange> scoreRanges = new ArrayList<>();

    public AssessmentType() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getTypeKey() { return typeKey; }
    public String getDuration() { return duration; }
    public List<AssessmentQuestion> getQuestions() { return questions; }
    public List<AssessmentChoice> getChoices() { return choices; }
    public List<ScoreRange> getScoreRanges() { return scoreRanges; }
}
