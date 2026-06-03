package com.mindlink.chatcluster;

import com.mindlink.domain.AssessmentResult;
import com.mindlink.domain.User;
import com.mindlink.repository.AssessmentResultRepository;
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
    private final AssessmentResultRepository assessmentResultRepository;
    private final UserRepository userRepository;
    private final ClusterVisualizationService visualizationService;

    @Value("${chat.cluster.weight.stress:1.0}")
    private double weightStress;

    @Value("${chat.cluster.weight.depression:1.2}")
    private double weightDepression;

    @Value("${chat.cluster.weight.anxiety:1.0}")
    private double weightAnxiety;

    public ClusterProfileService(UserAssessmentProfileRepository repository,
                                  AssessmentResultRepository assessmentResultRepository,
                                  UserRepository userRepository,
                                  ClusterVisualizationService visualizationService) {
        this.repository = repository;
        this.assessmentResultRepository = assessmentResultRepository;
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
        applyScores(profile, userId, displayName, stress, depression, anxiety);
        return persistWithClusterAssign(profile);
    }

    /**
     * 자가진단 1건 완료 시 해당 축만 갱신 (stress / depression / anxiety).
     * 번아웃(CBI)은 3축 스케일과 달라 클러스터 좌표에 반영하지 않는다.
     */
    @Transactional
    public void mergeAssessmentScore(Long userId, String displayName, String typeKey, int score) {
        if (userId == null || typeKey == null) return;
        String axis = axisForTypeKey(typeKey);
        if (axis == null) return;

        double value = score;
        clampOrThrow(value, maxForAxis(axis), axis);

        UserAssessmentProfile profile = repository.findByUserId(userId).orElseGet(UserAssessmentProfile::new);
        double stress = profile.getStressScore() != null ? profile.getStressScore() : 0;
        double depression = profile.getDepressionScore() != null ? profile.getDepressionScore() : 0;
        double anxiety = profile.getAnxietyScore() != null ? profile.getAnxietyScore() : 0;
        switch (axis) {
            case "stress" -> stress = value;
            case "depression" -> depression = value;
            case "anxiety" -> anxiety = value;
            default -> { return; }
        }
        applyScores(profile, userId, displayName, stress, depression, anxiety);
        persistWithClusterAssign(profile);
    }

    /** assessment_results 최신 점수로 실사용자 프로필 일괄 동기화 (관리자 recompute 직전 호출). */
    @Transactional
    public int backfillAllFromAssessmentResults() {
        List<User> users = assessmentResultRepository.findDistinctUsersWithAxisAssessments();
        int count = 0;
        for (User user : users) {
            if (syncFromLatestAssessments(user)) count++;
        }
        if (count > 0) {
            visualizationService.invalidateCache();
        }
        return count;
    }

    /** 최신 stress/depression/anxiety 검사 결과가 있으면 프로필 갱신. */
    @Transactional
    public boolean syncFromLatestAssessments(User user) {
        if (user == null || user.getId() == null) return false;

        Optional<Double> stress = latestAxisScore(user, "stress");
        Optional<Double> depression = latestAxisScore(user, "depression");
        Optional<Double> anxiety = latestAxisScore(user, "anxiety");
        if (stress.isEmpty() && depression.isEmpty() && anxiety.isEmpty()) {
            return false;
        }

        UserAssessmentProfile profile = repository.findByUserId(user.getId()).orElseGet(UserAssessmentProfile::new);
        double s = stress.orElse(profile.getStressScore() != null ? profile.getStressScore() : 0);
        double d = depression.orElse(profile.getDepressionScore() != null ? profile.getDepressionScore() : 0);
        double a = anxiety.orElse(profile.getAnxietyScore() != null ? profile.getAnxietyScore() : 0);
        String name = user.getName() != null && !user.getName().isBlank() ? user.getName() : "사용자";
        applyScores(profile, user.getId(), name, s, d, a);
        persistWithClusterAssign(profile);
        return true;
    }

    private UserAssessmentProfile persistWithClusterAssign(UserAssessmentProfile profile) {
        UserAssessmentProfile saved = repository.save(profile);
        Integer cid = nearestClusterId(saved);
        if (cid != null) {
            saved.setClusterId(cid);
            saved = repository.save(saved);
        }
        visualizationService.invalidateCache();
        return saved;
    }

    private void applyScores(UserAssessmentProfile profile, Long userId, String displayName,
                             double stress, double depression, double anxiety) {
        profile.setUserId(userId);
        if (profile.getPersonaKey() == null || profile.getPersonaKey().isBlank()) {
            profile.setPersonaKey("real_user_" + userId);
        }
        profile.setPersonaLabel(displayName != null && !displayName.isBlank() ? displayName : "나");
        profile.setPersonaStory("내 자가진단 결과로 만든 프로필이에요.");
        profile.setStressScore(stress);
        profile.setDepressionScore(depression);
        profile.setAnxietyScore(anxiety);
        profile.setStressNorm(round4(stress / AXIS_S_MAX));
        profile.setDepressionNorm(round4(depression / AXIS_D_MAX));
        profile.setAnxietyNorm(round4(anxiety / AXIS_A_MAX));
        profile.setIsSynthetic(0);
    }

    private Optional<Double> latestAxisScore(User user, String typeKey) {
        return assessmentResultRepository.findTopByUserAndTypeKeyOrderByCompletedAtDesc(user, typeKey)
                .map(AssessmentResult::getScore)
                .filter(s -> s != null)
                .map(Integer::doubleValue);
    }

    private static String axisForTypeKey(String typeKey) {
        return switch (typeKey) {
            case "stress" -> "stress";
            case "depression" -> "depression";
            case "anxiety" -> "anxiety";
            default -> null;
        };
    }

    private static double maxForAxis(String axis) {
        return switch (axis) {
            case "stress" -> AXIS_S_MAX;
            case "depression" -> AXIS_D_MAX;
            case "anxiety" -> AXIS_A_MAX;
            default -> throw new IllegalArgumentException("unknown axis: " + axis);
        };
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

    /** 내 클러스터·같은 집단 페르소나 라벨 상위 5 + 동적 정서 유형 해석. */
    public ClusterDtos.MyClusterResponse buildMyClusterResponse(Long userId) {
        Optional<UserAssessmentProfile> opt = repository.findByUserId(userId);
        if (opt.isEmpty() || opt.get().getClusterId() == null) {
            return new ClusterDtos.MyClusterResponse(false, null, -1, 0, List.of(),
                    null, null,
                    "아직 내 점수가 저장되지 않았어요. 자가진단을 완료한 뒤 다시 확인해 주세요.");
        }
        UserAssessmentProfile me = opt.get();
        int cid = me.getClusterId();
        List<UserAssessmentProfile> all = repository.findAll();
        int sameClusterCount = 0;
        double sumS = 0, sumD = 0, sumA = 0;  // 같은 군집 평균좌표(centroid) 누적
        Map<String, Integer> personaCount = new LinkedHashMap<>();
        for (UserAssessmentProfile p : all) {
            if (p.getClusterId() != null && p.getClusterId() == cid) {
                sameClusterCount++;
                sumS += p.getStressNorm();
                sumD += p.getDepressionNorm();
                sumA += p.getAnxietyNorm();
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

        // 군집 평균좌표로 유형을 동적 분류 (K-Means recompute 후에도 의미가 유지됨)
        int n = Math.max(1, sameClusterCount);
        String[] type = classifyType(sumS / n, sumD / n, sumA / n);
        String clusterLabel = type[0];
        String clusterDescription = type[1];

        String summary = "당신은 '" + clusterLabel + "'에 가까워요. " +
                "비슷한 마음의 또래가 " + sameClusterCount + "명 있고, " +
                "대표적인 이야기는 " +
                (topLabels.isEmpty() ? "곧 채워질 거예요" : String.join(", ", topLabels)) + " 예요.";

        return new ClusterDtos.MyClusterResponse(true, mePoint, cid, sameClusterCount,
                topLabels, clusterLabel, clusterDescription, summary);
    }

    /**
     * 정규화 평균좌표(0~1)로 정서 유형을 분류한다.
     * 시드 시점의 cluster_id 의미(0=안정…)에 의존하지 않고 좌표만 보므로 recompute 후에도 안전.
     * @return [라벨, 설명]
     */
    private static String[] classifyType(double s, double d, double a) {
        double max = Math.max(s, Math.max(d, a));
        if (max < 0.40) {
            return new String[]{"안정·회복형",
                    "스트레스·우울·불안 세 축이 모두 비교적 낮은, 회복 탄력이 좋은 유형이에요. 지금의 루틴을 유지해 보세요."};
        }
        int highCount = (s >= 0.55 ? 1 : 0) + (d >= 0.55 ? 1 : 0) + (a >= 0.55 ? 1 : 0);
        if (highCount >= 2) {
            return new String[]{"복합·소진형",
                    "여러 영역이 동시에 높게 나타나는 복합 유형이에요. 혼자 버티기보다 전문가 상담을 함께 고려해 보세요."};
        }
        if (s >= d && s >= a) {
            return new String[]{"스트레스 우세형",
                    "세 축 중 스트레스가 가장 두드러져요. 짧은 휴식과 호흡 같은 즉각적인 이완이 도움이 돼요."};
        }
        if (d >= a) {
            return new String[]{"우울 우세형",
                    "우울감이 상대적으로 높게 나타나요. 작은 활동부터 천천히 시작하고, 마음을 혼자 두지 마세요."};
        }
        return new String[]{"불안 우세형",
                "불안이 상대적으로 높아요. 걱정을 글로 적어보거나 호흡을 고르는 연습이 도움이 될 수 있어요."};
    }

    /**
     * E-1 — 실사용자(페르소나 제외)를 정서 유형 5분류(일반/스트레스/우울/불안/복합)로 집계.
     * 고정 5행(인원 0이어도 표시), 각 유형 인원·평균 norm.
     */
    public List<ClusterDtos.RealClusterStat> realCategoryStats() {
        String[] labels = {"일반", "스트레스", "우울", "불안", "복합"};
        long[] cnt = new long[5];
        double[] sumS = new double[5], sumD = new double[5], sumA = new double[5];
        for (UserAssessmentProfile p : repository.findAllByIsSynthetic(0)) {
            double s = p.getStressNorm()     != null ? p.getStressNorm()     : 0;
            double d = p.getDepressionNorm() != null ? p.getDepressionNorm() : 0;
            double a = p.getAnxietyNorm()    != null ? p.getAnxietyNorm()    : 0;
            int i = categoryIndex(s, d, a);
            cnt[i]++; sumS[i] += s; sumD[i] += d; sumA[i] += a;
        }
        List<ClusterDtos.RealClusterStat> out = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            long c = cnt[i];
            out.add(new ClusterDtos.RealClusterStat(
                    i, c,
                    round4(c > 0 ? sumS[i] / c : 0),
                    round4(c > 0 ? sumD[i] / c : 0),
                    round4(c > 0 ? sumA[i] / c : 0),
                    labels[i]));
        }
        return out;
    }

    /** 정서 유형 5분류 인덱스 (0 일반·안정, 1 스트레스, 2 우울, 3 불안, 4 복합) — classifyType 과 동일 임계값. */
    private static int categoryIndex(double s, double d, double a) {
        double max = Math.max(s, Math.max(d, a));
        if (max < 0.40) return 0;                                       // 일반(안정·회복)
        int high = (s >= 0.55 ? 1 : 0) + (d >= 0.55 ? 1 : 0) + (a >= 0.55 ? 1 : 0);
        if (high >= 2) return 4;                                        // 복합
        if (s >= d && s >= a) return 1;                                 // 스트레스
        if (d >= a) return 2;                                           // 우울
        return 3;                                                       // 불안
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
