package com.mindlink.service;

import com.mindlink.domain.DiagnosisResult;
import com.mindlink.domain.User;
import com.mindlink.dto.DiagnosisResultResponse;
import com.mindlink.repository.DiagnosisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DiagnosisService {

    private final DiagnosisRepository diagnosisRepository;

    public DiagnosisService(DiagnosisRepository diagnosisRepository) {
        this.diagnosisRepository = diagnosisRepository;
    }

    /**
     * 점수 계산, 위험군 분류, 결과 저장을 처리합니다.
     * user가 null이면 비로그인 사용자로 저장하지 않고 결과만 반환합니다.
     */
    @Transactional
    public DiagnosisResultResponse evaluate(String testType, List<Integer> answers, User user) {
        int score = answers == null ? 0 : answers.stream().mapToInt(Integer::intValue).sum();

        String level;
        String message;
        if (score <= 4) {
            level = "정상";
            message = "현재 증상이 거의 없는 상태입니다.";
        } else if (score <= 9) {
            level = "경미";
            message = "가벼운 증상이 있습니다. 자기 관리에 신경 써주세요.";
        } else if (score <= 14) {
            level = "중등도";
            message = "중간 정도의 증상이 있습니다. 전문가 상담을 고려해보세요.";
        } else {
            level = "심각";
            message = "심한 증상이 있습니다. 전문가의 도움이 필요합니다.";
        }

        // 로그인한 사용자면 결과 저장
        if (user != null) {
            DiagnosisResult result = new DiagnosisResult(user, testType, score, level);
            diagnosisRepository.save(result);
        }

        return new DiagnosisResultResponse(testType, score, level, message, score >= 10);
    }

    public List<DiagnosisResult> findByUser(Long userId) {
        return diagnosisRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
