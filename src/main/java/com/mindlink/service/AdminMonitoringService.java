package com.mindlink.service;

import com.mindlink.chatcluster.ClusterVisualizationService;
import com.mindlink.chatcluster.UserAssessmentProfile;
import com.mindlink.chatcluster.UserAssessmentProfileRepository;
import com.mindlink.domain.AssessmentResult;
import com.mindlink.domain.User;
import com.mindlink.repository.AssessmentResultRepository;
import com.mindlink.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminMonitoringService {

    public record ClusterSummary(int clusterId, String label, long memberCount, String subText) {}

    public record HighRiskUserInfo(Long userId, String name, String email,
                                   String typeKey, String typeName, String level,
                                   LocalDateTime completedAt) {}

    private final ClusterVisualizationService vizService;
    private final UserAssessmentProfileRepository profileRepo;
    private final AssessmentResultRepository resultRepo;
    private final UserRepository userRepository;

    public AdminMonitoringService(ClusterVisualizationService vizService,
                                  UserAssessmentProfileRepository profileRepo,
                                  AssessmentResultRepository resultRepo,
                                  UserRepository userRepository) {
        this.vizService      = vizService;
        this.profileRepo     = profileRepo;
        this.resultRepo      = resultRepo;
        this.userRepository  = userRepository;
    }

    // ===== 클러스터 요약 카드 =====

    public List<ClusterSummary> getClusterSummaries() {
        // 실사용자 클러스터별 수
        Map<Integer, Long> countMap = new LinkedHashMap<>();
        for (Object[] row : profileRepo.countRealUsersByCluster()) {
            Integer cid = (Integer) row[0];
            Long    cnt = (Long)    row[1];
            if (cid != null) countMap.put(cid, cnt);
        }

        // 중심 좌표 → 레이블 도출
        Map<Integer, double[]> centroidMap = new LinkedHashMap<>();
        vizService.visualization(true, true, null, null)
                  .centroids()
                  .forEach(c -> centroidMap.put(c.clusterId(),
                          new double[]{c.centroidS(), c.centroidD(), c.centroidA()}));

        List<ClusterSummary> result = new ArrayList<>();
        for (Map.Entry<Integer, double[]> e : centroidMap.entrySet()) {
            int cid = e.getKey();
            double[] coords = e.getValue();
            String label   = deriveLabel(coords[0], coords[1], coords[2]);
            String subText = deriveSubText(coords[0], coords[1], coords[2]);
            long count = countMap.getOrDefault(cid, 0L);
            result.add(new ClusterSummary(cid, label, count, subText));
        }
        result.sort(Comparator.comparingInt(ClusterSummary::clusterId));
        return result;
    }

    private String deriveLabel(double s, double d, double a) {
        if (s >= 0.55 && d >= 0.55 && a >= 0.55) return "고위험";
        if (s < 0.3  && d < 0.3  && a < 0.3)    return "안정";

        // 가장 높은 축을 지배 축으로 결정
        double max = Math.max(s, Math.max(d, a));
        String dominant;
        double second;
        if (max == d) { dominant = "우울";     second = Math.max(s, a); }
        else if (max == a) { dominant = "불안"; second = Math.max(s, d); }
        else               { dominant = "스트레스"; second = Math.max(d, a); }

        // 2위 축이 1위와 0.1 이내이고 둘 다 중간 이상이면 복합
        if ((max - second) < 0.1 && second >= 0.4) return "복합";
        return dominant;
    }

    private String deriveSubText(double s, double d, double a) {
        double rawS = s * 40;  // PSS-10 만점 40
        double rawD = d * 27;  // PHQ-9  만점 27
        double rawA = a * 21;  // GAD-7  만점 21
        return "스트레스 " + stressLevel(rawS)
             + " · 우울 " + depressionLevel(rawD)
             + " · 불안 " + anxietyLevel(rawA);
    }

    private static String stressLevel(double score) {
        if (score < 14) return "낮음";
        if (score < 27) return "보통";
        return "높음";
    }

    private static String depressionLevel(double score) {
        if (score < 5)  return "최소";
        if (score < 10) return "경증";
        if (score < 15) return "중등도";
        if (score < 20) return "중등도-중증";
        return "중증";
    }

    private static String anxietyLevel(double score) {
        if (score < 5)  return "최소";
        if (score < 10) return "경증";
        if (score < 15) return "중등도";
        return "중증";
    }

    // ===== 고위험군 유저 목록 =====

    public List<HighRiskUserInfo> getHighRiskUsers() {
        Map<Long, HighRiskUserInfo> infoMap = new LinkedHashMap<>();

        // 1. K-Means "고위험" 클러스터 실사용자
        Set<Integer> highRiskClusterIds = vizService.visualization(true, true, null, null)
                .centroids().stream()
                .filter(c -> "고위험".equals(deriveLabel(c.centroidS(), c.centroidD(), c.centroidA())))
                .map(c -> c.clusterId())
                .collect(Collectors.toSet());

        if (!highRiskClusterIds.isEmpty()) {
            for (UserAssessmentProfile p : profileRepo.findAllByIsSynthetic(0)) {
                if (p.getClusterId() != null && highRiskClusterIds.contains(p.getClusterId())) {
                    Long uid = p.getUserId();
                    if (uid != null) {
                        userRepository.findById(uid).ifPresent(u ->
                            infoMap.put(uid, new HighRiskUserInfo(
                                    uid, u.getName(), u.getEmail(),
                                    null, "K-Means 고위험", null, null))
                        );
                    }
                }
            }
        }

        // 2. 임상 고위험(assessment_results.is_high_risk = true) 병합
        List<AssessmentResult> clinical = resultRepo.findByHighRiskTrueOrderByCompletedAtDesc();
        for (AssessmentResult r : clinical) {
            Long uid = r.getUser().getId();
            infoMap.putIfAbsent(uid, new HighRiskUserInfo(
                    uid, r.getUser().getName(), r.getUser().getEmail(),
                    r.getTypeKey(), r.getTypeName(), r.getLevel(),
                    r.getCompletedAt()
            ));
        }

        return new ArrayList<>(infoMap.values());
    }
}
