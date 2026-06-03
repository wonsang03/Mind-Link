package com.mindlink.service;

import com.mindlink.chatcluster.UserAssessmentProfile;
import com.mindlink.chatcluster.UserAssessmentProfileRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * 커뮤니티 정렬용 카테고리별 개인 관심도(norm 0~1).
     * 스트레스/우울/불안 3개 카테고리만 담으며, 프로필이 없으면 빈 Map.
     * (인간관계·일상·기타 등은 호출 측에서 중립 기본값으로 처리)
     */
    public Map<String, Double> resolveCategoryAffinity(Long userId) {
        if (userId == null) return Map.of();
        Optional<UserAssessmentProfile> profileOpt = profileRepo.findByUserId(userId);
        if (profileOpt.isEmpty()) return Map.of();
        UserAssessmentProfile p = profileOpt.get();
        Map<String, Double> affinity = new HashMap<>();
        affinity.put("스트레스", p.getStressNorm()     != null ? p.getStressNorm()     : 0.0);
        affinity.put("우울",     p.getDepressionNorm() != null ? p.getDepressionNorm() : 0.0);
        affinity.put("불안",     p.getAnxietyNorm()    != null ? p.getAnxietyNorm()    : 0.0);
        return affinity;
    }

    /** 추천 페이지 맞춤순 정렬용 — EmotionCategory 키(STRESS/DEPRESSION/ANXIETY)별 norm. 프로필 없으면 빈 Map. */
    public Map<String, Double> resolveEmotionNorms(Long userId) {
        if (userId == null) return Map.of();
        Optional<UserAssessmentProfile> profileOpt = profileRepo.findByUserId(userId);
        if (profileOpt.isEmpty()) return Map.of();
        UserAssessmentProfile p = profileOpt.get();
        Map<String, Double> m = new HashMap<>();
        m.put("STRESS",     p.getStressNorm()     != null ? p.getStressNorm()     : 0.0);
        m.put("DEPRESSION", p.getDepressionNorm() != null ? p.getDepressionNorm() : 0.0);
        m.put("ANXIETY",    p.getAnxietyNorm()    != null ? p.getAnxietyNorm()    : 0.0);
        return m;
    }

    /**
     * 맞춤 추천 기본 감정 — 우세 축을 EmotionCategory 키(STRESS/DEPRESSION/ANXIETY)로 반환.
     * 프로필이 없거나 세 축 모두 유의미하지 않으면(<0.2) null.
     */
    public String resolveDominantEmotion(Long userId) {
        if (userId == null) return null;
        Optional<UserAssessmentProfile> profileOpt = profileRepo.findByUserId(userId);
        if (profileOpt.isEmpty()) return null;
        UserAssessmentProfile p = profileOpt.get();
        double s = p.getStressNorm()     != null ? p.getStressNorm()     : 0;
        double d = p.getDepressionNorm() != null ? p.getDepressionNorm() : 0;
        double a = p.getAnxietyNorm()    != null ? p.getAnxietyNorm()    : 0;
        double max = Math.max(s, Math.max(d, a));
        if (max < 0.2) return null;
        if (s == max) return "STRESS";
        if (d == max) return "DEPRESSION";
        return "ANXIETY";
    }
}
