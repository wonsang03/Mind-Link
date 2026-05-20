package com.mindlink.service;

import com.mindlink.domain.AssessmentType;
import com.mindlink.domain.ScoreRange;
import com.mindlink.repository.AssessmentTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AssessmentService {

    private final AssessmentTypeRepository assessmentTypeRepository;

    public AssessmentService(AssessmentTypeRepository assessmentTypeRepository) {
        this.assessmentTypeRepository = assessmentTypeRepository;
    }

    public List<AssessmentType> findAll() {
        return assessmentTypeRepository.findAll();
    }

    public Optional<AssessmentType> findByTypeKey(String typeKey) {
        return assessmentTypeRepository.findByTypeKey(typeKey);
    }

    public ScoreRange evaluate(AssessmentType type, int totalScore) {
        return type.getScoreRanges().stream()
                .filter(r -> totalScore >= r.getMinScore() && totalScore <= r.getMaxScore())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        type.getTypeKey() + " 점수 범위 미등록: " + totalScore));
    }
}
