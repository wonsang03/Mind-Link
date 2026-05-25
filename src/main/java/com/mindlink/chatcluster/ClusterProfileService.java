package com.mindlink.chatcluster;

import com.mindlink.domain.User;
import com.mindlink.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static com.mindlink.chatcluster.ClusterVisualizationService.AXIS_A_MAX;
import static com.mindlink.chatcluster.ClusterVisualizationService.AXIS_D_MAX;
import static com.mindlink.chatcluster.ClusterVisualizationService.AXIS_S_MAX;

/**
 * 실사용자 프로필 저장 / 조회 / 검색.
 *  - {@link #saveOrUpdateUserProfile} : 자가진단 결과 저장 → 가장 가까운 centroid 로 cluster_id assign
 *  - {@link #buildMyClusterResponse} : 내 위치·같은 집단 페르소나 상위 5
 *  - {@link #searchRealUsersByName}  : 이름·라벨·이메일 부분 일치 (Repository 의 JPQL)
 *
 * 저장·변경 직후 {@link ClusterVisualizationService#invalidateCache()} 로 viz 캐시 무효.
 */
@Service
public class ClusterProfileService {

    private final UserAssessmentProfileRepository repository;
    private final UserRepository userRepository;
    private final ClusterVisualizationService visualizationService;

    @Value("${chat.cluster.weight.stress:1.0}")
    private double weightStress;

    @Value("${chat.cluster.weight.depression:1.2}")
    private double weightDepression;

    @Value("${chat.cluster.weight.anxiety:1.0}")
    private double weightAnxiety;

    public ClusterProfileService(UserAssessmentProfileRepository repository,
                                  UserRepository userRepository,
                                  ClusterVisualizationService visualizationService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.visualizationService = visualizationService;
    }

    @Transactional
    public UserAssessmentProfile saveOrUpdateUserProfile(Long userId, String displayName,
                                                          double stress, double depression, double anxiety) {
        clampOrThrow(stress, AXIS_S_MAX, "stress");
        clampOrThrow(depression, AXIS_D_MAX, "depression");
        clampOrThrow(anxiety, AXIS_A_MAX, "anxiety");

        UserAssessmentProfile profile = repository.findByUserId(userId).orElseGet(UserAssessmentProfile::new);
        profile.setUserId(userId);
        profile.setPersonaKey("real_user_" + userId);
        profile.setPersonaLabel(displayName != null && !displayName.isBlank() ? displayName : "나");
        profile.setPersonaStory("내 자가진단 결과로 만든 프로필이에요.");
        profile.setStressScore(stress);
        profile.setDepressionScore(depression);
        profile.setAnxietyScore(anxiety);
        profile.setStressNorm(round4(stress / AXIS_S_MAX));
        profile.setDepressionNorm(round4(depression / AXIS_D_MAX));
        profile.setAnxietyNorm(round4(anxiety / AXIS_A_MAX));
        profile.setIsSynthetic(0);
        UserAssessmentProfile saved = repository.save(profile);

        // 신규 점 추가 시 가장 가까운 centroid 로 assign (전체 recompute 호출 비용 회피)
        Integer cid = nearestClusterId(saved);
        if (cid != null) {
            saved.setClusterId(cid);
            repository.save(saved);
        }
        visualizationService.invalidateCache();
        return saved;
    }

    public Optional<UserAssessmentProfile> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    /** 실사용자 이름(또는 프로필 라벨·이메일) 부분 일치 검색 — 3D 맵 찾기용. */
    public ClusterDtos.SearchResponse searchRealUsersByName(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return new ClusterDtos.SearchResponse(q, 0, List.of());
        }
        String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
        List<UserAssessmentProfile> found = repository.searchRealUsers(pattern);

        List<ClusterDtos.SearchMatch> matches = new ArrayList<>(found.size());
        for (UserAssessmentProfile p : found) {
            String email = userRepository.findById(p.getUserId())
                    .map(User::getEmail)
                    .orElse("");
            matches.add(new ClusterDtos.SearchMatch(
                    p.getId(),
                    p.getUserId(),
                    p.getPersonaLabel(),
                    email,
                    p.getStressScore(),
                    p.getDepressionScore(),
                    p.getAnxietyScore(),
                    p.getStressNorm(),
                    p.getDepressionNorm(),
                    p.getAnxietyNorm(),
                    p.getClusterId()));
        }
        matches.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return new ClusterDtos.SearchResponse(q, matches.size(), matches);
    }

    /** 내 클러스터·같은 집단 페르소나 라벨 상위 5. */
    public ClusterDtos.MyClusterResponse buildMyClusterResponse(Long userId) {
        Optional<UserAssessmentProfile> opt = repository.findByUserId(userId);
        if (opt.isEmpty() || opt.get().getClusterId() == null) {
            return new ClusterDtos.MyClusterResponse(false, null, -1, 0, List.of(),
                    "아직 내 점수가 저장되지 않았어요. 자가진단을 완료한 뒤 다시 확인해 주세요.");
        }
        UserAssessmentProfile me = opt.get();
        int cid = me.getClusterId();
        List<UserAssessmentProfile> all = repository.findAll();
        int sameClusterCount = 0;
        Map<String, Integer> personaCount = new LinkedHashMap<>();
        for (UserAssessmentProfile p : all) {
            if (p.getClusterId() != null && p.getClusterId() == cid) {
                sameClusterCount++;
                if (p.isSynthetic()) {
                    personaCount.merge(p.getPersonaLabel(), 1, Integer::sum);
                }
            }
        }
        List<String> topLabels = personaCount.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        ClusterDtos.Point mePoint = new ClusterDtos.Point(
                me.getId(), me.getPersonaKey(), me.getPersonaLabel(),
                me.getStressNorm(), me.getDepressionNorm(), me.getAnxietyNorm(),
                me.getStressScore(), me.getDepressionScore(), me.getAnxietyScore(),
                me.getClusterId(), me.isSynthetic(), true);

        String summary = "당신은 집단 " + cid + " 에 속해 있어요. " +
                "총 " + sameClusterCount + "명이 비슷한 위치에 있고, 대표 유형은 " +
                (topLabels.isEmpty() ? "데이터 부족" : String.join(", ", topLabels)) + " 이에요.";

        return new ClusterDtos.MyClusterResponse(true, mePoint, cid, sameClusterCount, topLabels, summary);
    }

    private Integer nearestClusterId(UserAssessmentProfile me) {
        Map<Integer, double[]> sums = new LinkedHashMap<>();
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (UserAssessmentProfile p : repository.findAll()) {
            Integer cid = p.getClusterId();
            if (cid == null || p == me) continue;
            double[] s = sums.computeIfAbsent(cid, c -> new double[3]);
            s[0] += p.getStressNorm();
            s[1] += p.getDepressionNorm();
            s[2] += p.getAnxietyNorm();
            counts.merge(cid, 1, Integer::sum);
        }
        if (sums.isEmpty()) return null;

        double mx = me.getStressNorm();
        double my = me.getDepressionNorm();
        double mz = me.getAnxietyNorm();
        Integer bestCid = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (Map.Entry<Integer, double[]> e : sums.entrySet()) {
            int cid = e.getKey();
            int n = counts.get(cid);
            double cx = e.getValue()[0] / n;
            double cy = e.getValue()[1] / n;
            double cz = e.getValue()[2] / n;
            double dx = (mx - cx) * weightStress;
            double dy = (my - cy) * weightDepression;
            double dz = (mz - cz) * weightAnxiety;
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestDist) { bestDist = d; bestCid = cid; }
        }
        return bestCid;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static void clampOrThrow(double v, double max, String name) {
        if (v < 0 || v > max) {
            throw new IllegalArgumentException(name + " 점수가 허용 범위(0~" + (int) max + ")를 벗어났어요.");
        }
    }
}
