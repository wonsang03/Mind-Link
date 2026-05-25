package com.mindlink.chatcluster;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 3D 시각화 응답 빌더 + TTL 캐시.
 *  - {@link #visualization(boolean, boolean, Integer, Long)} : viewer 가 호출하는 진입점
 *  - 캐시 무효화는 {@link #invalidateCache()} — recompute / 프로필 저장 시점에 호출
 *  - 점·centroid·축·가중치·통계 → {@link ClusterDtos.VisualizationResponse}
 */
@Service
public class ClusterVisualizationService {

    static final int AXIS_S_MAX = 40;
    static final int AXIS_D_MAX = 27;
    static final int AXIS_A_MAX = 21;

    private final UserAssessmentProfileRepository repository;

    @Value("${chat.cluster.k:6}")
    private int k;

    @Value("${chat.cluster.weight.stress:1.0}")
    private double weightStress;

    @Value("${chat.cluster.weight.depression:1.2}")
    private double weightDepression;

    @Value("${chat.cluster.weight.anxiety:1.0}")
    private double weightAnxiety;

    @Value("${chat.cluster.viz.cache-seconds:30}")
    private int vizCacheSeconds;

    @Value("${chat.cluster.viz.max-points:500}")
    private int vizMaxPoints;

    /** 시각화 응답 캐시 — recompute / saveProfile 호출 또는 TTL 만료 시 무효. */
    private final AtomicReference<CachedViz> vizCache = new AtomicReference<>();

    public ClusterVisualizationService(UserAssessmentProfileRepository repository) {
        this.repository = repository;
    }

    /** recompute / 프로필 저장 직후 즉시 무효화하는 훅. */
    public void invalidateCache() {
        vizCache.set(null);
    }

    public ClusterDtos.VisualizationResponse visualization(boolean includeSynthetic,
                                                            boolean includeReal,
                                                            Integer highlightCluster,
                                                            Long currentUserId) {
        CachedViz cached = vizCache.get();
        long now = System.currentTimeMillis();
        ClusterDtos.VisualizationResponse base;
        if (cached != null && (now - cached.builtAt) < vizCacheSeconds * 1000L) {
            base = cached.response;
        } else {
            base = buildVisualization();
            vizCache.set(new CachedViz(base, now));
        }

        // 필터·하이라이트는 캐시 후 단계에서 적용 (currentUser 표시도 마찬가지)
        List<ClusterDtos.Point> filtered = new ArrayList<>();
        int syn = 0, real = 0;
        for (ClusterDtos.Point p : base.points()) {
            if (!includeSynthetic && p.synthetic()) continue;
            if (!includeReal && !p.synthetic()) continue;
            boolean isMe = currentUserId != null
                    && !p.synthetic()
                    && p.id() != null
                    && currentUserId.equals(extractUserIdFromPoint(p));
            ClusterDtos.Point copy = new ClusterDtos.Point(
                    p.id(), p.personaKey(), p.personaLabel(),
                    p.stressNorm(), p.depressionNorm(), p.anxietyNorm(),
                    p.stressScore(), p.depressionScore(), p.anxietyScore(),
                    p.clusterId(), p.synthetic(), isMe);
            filtered.add(copy);
            if (p.synthetic()) syn++; else real++;
        }

        ClusterDtos.Stats baseStats = base.stats();
        ClusterDtos.Stats stats = new ClusterDtos.Stats(
                filtered.size(), syn, real,
                baseStats.silhouette(), baseStats.iterations(), baseStats.elapsedMillis());

        return new ClusterDtos.VisualizationResponse(
                base.k(), base.axes(), base.weights(),
                filtered, base.centroids(), stats);
    }

    /** Point.id 는 profile id 라 user_id 추출 불가 — 별도 조회로 대체. */
    private Long extractUserIdFromPoint(ClusterDtos.Point p) {
        if (p.id() == null) return null;
        return repository.findById(p.id())
                .map(UserAssessmentProfile::getUserId)
                .orElse(null);
    }

    private ClusterDtos.VisualizationResponse buildVisualization() {
        long t0 = System.currentTimeMillis();
        List<UserAssessmentProfile> all = repository.findAll();
        if (all.size() > vizMaxPoints) {
            // 실사용자는 항상 포함, 합성만 잘라냄
            List<UserAssessmentProfile> real = all.stream().filter(p -> p.getUserId() != null).toList();
            List<UserAssessmentProfile> syn = new ArrayList<>(all.stream().filter(p -> p.getUserId() == null).toList());
            Collections.shuffle(syn, new Random(123));
            int synCap = Math.max(0, vizMaxPoints - real.size());
            all = new ArrayList<>(real);
            if (synCap > 0 && !syn.isEmpty()) {
                all.addAll(syn.subList(0, Math.min(syn.size(), synCap)));
            }
        }

        List<ClusterDtos.Point> points = new ArrayList<>(all.size());
        for (UserAssessmentProfile p : all) {
            points.add(new ClusterDtos.Point(
                    p.getId(), p.getPersonaKey(), p.getPersonaLabel(),
                    p.getStressNorm(), p.getDepressionNorm(), p.getAnxietyNorm(),
                    p.getStressScore(), p.getDepressionScore(), p.getAnxietyScore(),
                    p.getClusterId(), p.isSynthetic(), false));
        }

        // centroid — 현재 cluster_id 기준 평균 (K-Means 결과를 그대로 반영)
        Map<Integer, double[]> sums = new LinkedHashMap<>();
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (UserAssessmentProfile p : all) {
            Integer cid = p.getClusterId();
            if (cid == null) continue;
            double[] s = sums.computeIfAbsent(cid, c -> new double[3]);
            s[0] += p.getStressNorm();
            s[1] += p.getDepressionNorm();
            s[2] += p.getAnxietyNorm();
            counts.merge(cid, 1, Integer::sum);
        }
        List<ClusterDtos.Centroid> centroids = new ArrayList<>();
        for (Map.Entry<Integer, double[]> e : sums.entrySet()) {
            int cid = e.getKey();
            int n = counts.getOrDefault(cid, 1);
            double[] s = e.getValue();
            centroids.add(new ClusterDtos.Centroid(cid, n, s[0] / n, s[1] / n, s[2] / n));
        }
        centroids.sort((c1, c2) -> Integer.compare(c1.clusterId(), c2.clusterId()));

        ClusterDtos.Axes axes = new ClusterDtos.Axes(
                "스트레스(PSS)", "우울(PHQ-9)", "불안(GAD-7)",
                AXIS_S_MAX, AXIS_D_MAX, AXIS_A_MAX);
        ClusterDtos.Weights weights = new ClusterDtos.Weights(weightStress, weightDepression, weightAnxiety);

        int syn = 0, real = 0;
        for (UserAssessmentProfile p : all) {
            if (p.isSynthetic()) syn++; else real++;
        }
        // silhouette·iterations 는 recompute 시점 정보 — 캐시에는 마지막 점수가 없으므로 0 으로
        ClusterDtos.Stats stats = new ClusterDtos.Stats(
                all.size(), syn, real, 0.0, 0, System.currentTimeMillis() - t0);

        return new ClusterDtos.VisualizationResponse(k, axes, weights, points, centroids, stats);
    }

    private record CachedViz(ClusterDtos.VisualizationResponse response, long builtAt) {}
}
