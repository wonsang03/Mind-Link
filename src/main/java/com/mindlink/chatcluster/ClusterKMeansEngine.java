package com.mindlink.chatcluster;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.distance.DistanceMeasure;
import org.apache.commons.math3.random.JDKRandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 가중 K-Means + Silhouette 엔진.
 *  - 정규화 좌표(stressNorm, depressionNorm, anxietyNorm)에 √w 를 곱해 단순 유클리드로 동작
 *  - {@link #restarts} 번 재시작 → Silhouette 최고 결과 채택
 *  - centroid (s+d+a) 합 오름차순으로 정렬해 cluster_id 부여 (UI 색상 안정성)
 *  - 완료 후 {@link ClusterVisualizationService#invalidateCache()} 호출로 viz 캐시 무효화
 */
@Service
public class ClusterKMeansEngine {

    private static final Logger log = LoggerFactory.getLogger(ClusterKMeansEngine.class);

    private final UserAssessmentProfileRepository repository;
    private final ClusterProfileService profileService;
    private final ClusterVisualizationService visualizationService;

    @Value("${chat.cluster.k:6}")
    private int k;

    @Value("${chat.cluster.weight.stress:1.0}")
    private double weightStress;

    @Value("${chat.cluster.weight.depression:1.2}")
    private double weightDepression;

    @Value("${chat.cluster.weight.anxiety:1.0}")
    private double weightAnxiety;

    @Value("${chat.cluster.kmeans.max-iterations:500}")
    private int maxIterations;

    @Value("${chat.cluster.kmeans.restarts:10}")
    private int restarts;

    public ClusterKMeansEngine(UserAssessmentProfileRepository repository,
                                ClusterProfileService profileService,
                                ClusterVisualizationService visualizationService) {
        this.repository = repository;
        this.profileService = profileService;
        this.visualizationService = visualizationService;
    }

    /** DB 전체 프로필을 클러스터링 → cluster_id 갱신 → viz 캐시 무효화. */
    @Transactional
    public ClusterDtos.RecomputeResponse recompute() {
        long t0 = System.currentTimeMillis();
        int backfilled = profileService.backfillAllFromAssessmentResults();
        if (backfilled > 0) {
            log.info("Cluster backfill from assessment_results: {} users", backfilled);
        }
        List<UserAssessmentProfile> all = repository.findAll();
        if (all.size() < k) {
            log.warn("Cluster recompute skipped — points={} < k={}", all.size(), k);
            return new ClusterDtos.RecomputeResponse(k, all.size(), 0.0, 0, System.currentTimeMillis() - t0);
        }

        List<WeightedPoint> wps = new ArrayList<>(all.size());
        for (UserAssessmentProfile p : all) {
            wps.add(new WeightedPoint(p, weightStress, weightDepression, weightAnxiety));
        }

        BestRun best = null;
        long seed = 42L;
        for (int r = 0; r < restarts; r++) {
            JDKRandomGenerator rng = new JDKRandomGenerator();
            rng.setSeed(seed + r);
            KMeansPlusPlusClusterer<WeightedPoint> clusterer =
                    new KMeansPlusPlusClusterer<>(k, maxIterations, new EuclidWeighted(), rng);
            List<CentroidCluster<WeightedPoint>> result = clusterer.cluster(wps);
            double sil = silhouette(result);
            if (best == null || sil > best.silhouette) {
                best = new BestRun(result, sil, maxIterations);
            }
        }

        // KMeans 가 재시작마다 클러스터 인덱스를 섞기 때문에 centroid 좌표 합 기준 정렬로 안정화
        List<CentroidCluster<WeightedPoint>> ordered = orderClustersByCentroid(best.result);
        for (int cid = 0; cid < ordered.size(); cid++) {
            for (WeightedPoint wp : ordered.get(cid).getPoints()) {
                wp.profile.setClusterId(cid);
            }
        }
        repository.saveAll(all);
        visualizationService.invalidateCache();

        long elapsed = System.currentTimeMillis() - t0;
        log.info("Cluster recompute done points={} k={} silhouette={} elapsed={}ms",
                all.size(), k, String.format("%.4f", best.silhouette), elapsed);

        return new ClusterDtos.RecomputeResponse(k, all.size(), best.silhouette, best.iterations, elapsed);
    }

    /** 평균 Silhouette — 가중 거리 기반. -1~+1, 클러스터 분리도가 좋을수록 +1 에 가깝다. */
    private double silhouette(List<CentroidCluster<WeightedPoint>> clusters) {
        Map<WeightedPoint, Integer> assign = new LinkedHashMap<>();
        for (int i = 0; i < clusters.size(); i++) {
            for (WeightedPoint p : clusters.get(i).getPoints()) {
                assign.put(p, i);
            }
        }
        if (clusters.size() < 2) return 0.0;

        double total = 0.0;
        int count = 0;
        for (Map.Entry<WeightedPoint, Integer> e : assign.entrySet()) {
            WeightedPoint p = e.getKey();
            int own = e.getValue();
            double a = meanDistance(p, clusters.get(own).getPoints(), true);
            double b = Double.POSITIVE_INFINITY;
            for (int j = 0; j < clusters.size(); j++) {
                if (j == own) continue;
                double m = meanDistance(p, clusters.get(j).getPoints(), false);
                if (m < b) b = m;
            }
            if (Double.isInfinite(b)) continue;
            double s = (b - a) / Math.max(a, b);
            if (Double.isNaN(s)) s = 0.0;
            total += s;
            count++;
        }
        return count == 0 ? 0.0 : total / count;
    }

    private static double meanDistance(WeightedPoint p, List<WeightedPoint> group, boolean excludeSelf) {
        double sum = 0.0;
        int n = 0;
        for (WeightedPoint q : group) {
            if (excludeSelf && q == p) continue;
            sum += euclid(p.getPoint(), q.getPoint());
            n++;
        }
        return n == 0 ? 0.0 : sum / n;
    }

    private static double euclid(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            s += d * d;
        }
        return Math.sqrt(s);
    }

    /** centroid (s+d+a) 오름차순 → 안정형 클러스터가 항상 cluster_id=0 부근에. */
    private static List<CentroidCluster<WeightedPoint>> orderClustersByCentroid(List<CentroidCluster<WeightedPoint>> clusters) {
        List<CentroidCluster<WeightedPoint>> copy = new ArrayList<>(clusters);
        copy.sort((c1, c2) -> {
            double[] a = c1.getCenter().getPoint();
            double[] b = c2.getCenter().getPoint();
            return Double.compare(a[0] + a[1] + a[2], b[0] + b[1] + b[2]);
        });
        return copy;
    }

    // ─────────────────────────────────────────────────────────────────────
    // 내부 타입
    // ─────────────────────────────────────────────────────────────────────

    /** commons-math3 Clusterable 래퍼 — 가중치를 좌표에 √w 로 곱해 넣어 단순 유클리드 → 가중 유클리드. */
    private static final class WeightedPoint implements Clusterable {
        final UserAssessmentProfile profile;
        final double[] point;

        WeightedPoint(UserAssessmentProfile p, double ws, double wd, double wa) {
            this.profile = p;
            this.point = new double[] {
                    p.getStressNorm() * Math.sqrt(ws),
                    p.getDepressionNorm() * Math.sqrt(wd),
                    p.getAnxietyNorm() * Math.sqrt(wa)
            };
        }

        @Override
        public double[] getPoint() { return point; }
    }

    /** 가중 좌표가 이미 적용되어 있으므로 단순 유클리드. */
    private static final class EuclidWeighted implements DistanceMeasure {
        @Override
        public double compute(double[] a, double[] b) {
            return euclid(a, b);
        }
    }

    private record BestRun(List<CentroidCluster<WeightedPoint>> result, double silhouette, int iterations) {}
}
