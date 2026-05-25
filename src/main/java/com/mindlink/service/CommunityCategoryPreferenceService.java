package com.mindlink.service;

import com.mindlink.chatcluster.UserAssessmentProfile;
import com.mindlink.chatcluster.UserAssessmentProfileRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 로그인 유저의 자가진단 프로필(stress/depression/anxiety norm)로 커뮤니티 추천 카테고리를 도출한다.
 * chatcluster 리포지토리 의존을 이 서비스 안에 가두어 CommunityController가 직접 결합하지 않도록 한다.
 */
@Service
public class CommunityCategoryPreferenceService {

    private final UserAssessmentProfileRepository profileRepo;

    public CommunityCategoryPreferenceService(UserAssessmentProfileRepository profileRepo) {
        this.profileRepo = profileRepo;
    }

    /** 프로필 norm 점수 → 추천 카테고리 1~2개 (임계값 0.2 / 0.3 유지) */
    public List<String> resolvePreferredCategories(Long userId) {
        if (userId == null) return List.of();
        Optional<UserAssessmentProfile> profileOpt = profileRepo.findByUserId(userId);
        if (profileOpt.isEmpty()) return List.of();
        UserAssessmentProfile p = profileOpt.get();
        double s = p.getStressNorm() != null ? p.getStressNorm() : 0;
        double d = p.getDepressionNorm() != null ? p.getDepressionNorm() : 0;
        double a = p.getAnxietyNorm() != null ? p.getAnxietyNorm() : 0;

        List<String> result = new ArrayList<>();
        // 최대값 기준 첫 번째 카테고리
        if (s >= d && s >= a && s > 0.2) result.add("스트레스");
        else if (d >= s && d >= a && d > 0.2) result.add("우울");
        else if (a > 0.2) result.add("불안");

        // 두 번째 카테고리 (첫 번째와 다른 축에서 0.3 이상)
        if (!result.isEmpty()) {
            String first = result.get(0);
            if (!"스트레스".equals(first) && s > 0.3) result.add("스트레스");
            else if (!"우울".equals(first) && d > 0.3) result.add("우울");
            else if (!"불안".equals(first) && a > 0.3) result.add("불안");
        }
        return result;
    }
}
