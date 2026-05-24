package com.mindlink.service;

import com.mindlink.domain.AssessmentResult;
import com.mindlink.domain.AssessmentType;
import com.mindlink.domain.ScoreRange;
import com.mindlink.domain.User;
import com.mindlink.repository.AssessmentResultRepository;
import com.mindlink.repository.AssessmentTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

    private final AssessmentTypeRepository assessmentTypeRepository;
    private final AssessmentResultRepository assessmentResultRepository;

    public AssessmentService(AssessmentTypeRepository assessmentTypeRepository,
                             AssessmentResultRepository assessmentResultRepository) {
        this.assessmentTypeRepository = assessmentTypeRepository;
        this.assessmentResultRepository = assessmentResultRepository;
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

    /**
     * 자가진단 결과를 저장합니다. assessment_results 테이블이 아직 생성되지 않은 환경에서도
     * 결과 화면 자체는 동작하도록 저장 실패는 경고 로그만 남기고 무시합니다.
     */
    @Transactional
    public void saveResult(User user, String typeKey, String typeName,
                           int score, String level, boolean highRisk) {
        try {
            assessmentResultRepository.save(
                    new AssessmentResult(user, typeKey, typeName, score, level, highRisk));
        } catch (Exception e) {
            log.warn("자가진단 결과 저장 실패 (assessment_results 테이블 확인 필요): {}", e.getMessage());
        }
    }
}
