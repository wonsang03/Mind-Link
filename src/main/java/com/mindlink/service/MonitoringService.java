package com.mindlink.service;

import com.mindlink.domain.AssessmentResult;
import com.mindlink.domain.User;
import com.mindlink.domain.UserAlert;
import com.mindlink.repository.AssessmentResultRepository;
import com.mindlink.repository.UserAlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MonitoringService {

    private static final Map<String, Integer> LEVEL_ORDER = new HashMap<>();
    static {
        // 우울·불안
        LEVEL_ORDER.put("최소", 0);
        LEVEL_ORDER.put("경증", 1);
        LEVEL_ORDER.put("중등도", 2);
        LEVEL_ORDER.put("중등도-중증", 3);
        LEVEL_ORDER.put("중증", 4);
        // 스트레스·번아웃
        LEVEL_ORDER.put("낮음", 0);
        LEVEL_ORDER.put("보통", 1);
        LEVEL_ORDER.put("높음", 2);
    }

    private final AssessmentResultRepository resultRepo;
    private final UserAlertRepository alertRepo;

    public MonitoringService(AssessmentResultRepository resultRepo,
                             UserAlertRepository alertRepo) {
        this.resultRepo = resultRepo;
        this.alertRepo  = alertRepo;
    }

    /**
     * 결과 저장 후 이전 결과와 비교하여 악화 시 알림 생성.
     * @return 악화 알림이 생성되면 해당 UserAlert, 없으면 null
     */
    @Transactional
    public UserAlert saveAndMonitor(User user, String typeKey,
                                    Integer score, String level, boolean highRisk,
                                    Integer personalScore, String personalLevel,
                                    Integer workScore, String workLevel) {

        // 저장 전에 이전 결과 조회 (저장 후 조회 시 현재 결과가 반환되는 문제 방지)
        Optional<AssessmentResult> prevOpt =
            resultRepo.findTopByUserAndTypeKeyOrderByCompletedAtDesc(user, typeKey);

        // 현재 결과 저장
        AssessmentResult current = new AssessmentResult();
        current.setUser(user);
        current.setTypeKey(typeKey);
        current.setTypeName(typeName(typeKey));
        current.setScore(score);
        current.setLevel(level);
        current.setResultLevel(level);
        current.setHighRisk(highRisk);
        current.setPersonalScore(personalScore);
        current.setPersonalLevel(personalLevel);
        current.setWorkScore(workScore);
        current.setWorkLevel(workLevel);
        current.setCompletedAt(LocalDateTime.now());
        resultRepo.save(current);

        UserAlert uiAlert = null;

        // 고위험 알림 (첫 검사 포함 항상 저장)
        if (highRisk) {
            UserAlert highRiskAlert = new UserAlert();
            highRiskAlert.setUser(user);
            highRiskAlert.setAlertType("HIGH_RISK");
            highRiskAlert.setMessage(buildHighRiskMessage(current));
            highRiskAlert.setAssessmentResult(current);
            highRiskAlert.setRead(false);
            highRiskAlert.setCreatedAt(LocalDateTime.now());
            uiAlert = alertRepo.save(highRiskAlert);
        }

        // 첫 검사: 이전 결과 없음 → 비교 알림 없음, 고위험 알림만 결과 화면에 표시
        if (prevOpt.isEmpty()) {
            return uiAlert;
        }

        AssessmentResult prev = prevOpt.get();

        // 중간 상태 유지 시 맞춤 추천 알림
        if (isMiddleLevel(prev) && isMiddleLevel(current)) {
            UserAlert recommendAlert = new UserAlert();
            recommendAlert.setUser(user);
            recommendAlert.setAlertType("RECOMMEND");
            recommendAlert.setMessage(buildRecommendMessage(current));
            recommendAlert.setAssessmentResult(current);
            recommendAlert.setRead(false);
            recommendAlert.setCreatedAt(LocalDateTime.now());
            uiAlert = alertRepo.save(recommendAlert);
        }

        // 긍정적 변화 감지 (개선 또는 최소 상태 유지)
        boolean improved    = isImprovement(prev, current);
        boolean maintainMin = isMinimumLevel(prev) && isMinimumLevel(current);
        if (improved || maintainMin) {
            boolean atMin = isMinimumLevel(current);
            UserAlert goodAlert = new UserAlert();
            goodAlert.setUser(user);
            goodAlert.setAlertType(atMin ? "IMPROVEMENT_MIN" : "IMPROVEMENT");
            goodAlert.setMessage(improved
                    ? buildImprovementMessage(prev, current)
                    : buildMaintainedMinMessage(current));
            goodAlert.setAssessmentResult(current);
            goodAlert.setRead(false);
            goodAlert.setCreatedAt(LocalDateTime.now());
            uiAlert = alertRepo.save(goodAlert);
        }

        // 악화 감지 (결과 화면 배너 우선)
        if (isDeterioration(prev, current)) {
            UserAlert alert = new UserAlert();
            alert.setUser(user);
            alert.setAlertType("DETERIORATION");
            alert.setMessage(buildMessage(prev, current));
            alert.setAssessmentResult(current);
            alert.setRead(false);
            alert.setCreatedAt(LocalDateTime.now());
            return alertRepo.save(alert);
        }

        return uiAlert;
    }

    private boolean isImprovement(AssessmentResult prev, AssessmentResult current) {
        if ("burnout".equals(current.getTypeKey())) {
            int prevMax = Math.max(order(prev.getPersonalLevel()), order(prev.getWorkLevel()));
            int currMax = Math.max(order(current.getPersonalLevel()), order(current.getWorkLevel()));
            return currMax < prevMax;
        }
        return order(current.getLevel()) < order(prev.getLevel());
    }

    private boolean isMinimumLevel(AssessmentResult r) {
        if ("burnout".equals(r.getTypeKey())) {
            return "낮음".equals(r.getPersonalLevel()) && "낮음".equals(r.getWorkLevel());
        }
        return "최소".equals(r.getLevel()) || "낮음".equals(r.getLevel());
    }

    private boolean isMiddleLevel(AssessmentResult r) {
        if ("burnout".equals(r.getTypeKey())) {
            String p = r.getPersonalLevel();
            String w = r.getWorkLevel();
            // 개인/업무 둘 다 "높음"은 아니고, 하나라도 "보통"인 상태
            return ("보통".equals(p) || "보통".equals(w))
                && !"높음".equals(p) && !"높음".equals(w);
        }
        // depression·anxiety: 경증·중등도 / stress: 보통
        return "경증".equals(r.getLevel()) || "중등도".equals(r.getLevel()) || "보통".equals(r.getLevel());
    }

    private boolean isDeterioration(AssessmentResult prev, AssessmentResult current) {
        if ("burnout".equals(current.getTypeKey())) {
            int prevMax = Math.max(order(prev.getPersonalLevel()), order(prev.getWorkLevel()));
            int currMax = Math.max(order(current.getPersonalLevel()), order(current.getWorkLevel()));
            return currMax > prevMax;
        }
        return order(current.getLevel()) > order(prev.getLevel());
    }

    private int order(String level) {
        return LEVEL_ORDER.getOrDefault(level, -1);
    }

    private String buildImprovementMessage(AssessmentResult prev, AssessmentResult current) {
        String name = typeName(current.getTypeKey());
        if ("burnout".equals(current.getTypeKey())) {
            return name + " 결과가 이전보다 나아졌어요. 스스로를 잘 돌보고 계시네요, 앞으로도 꾸준히 유지해 보아요!";
        }
        return name + " 결과가 [" + prev.getLevel() + "] → [" + current.getLevel()
                + "]로 나아졌어요. 스스로를 잘 돌보고 계시네요!";
    }

    private String buildMaintainedMinMessage(AssessmentResult current) {
        String name = typeName(current.getTypeKey());
        return name + " 결과가 건강한 상태를 유지하고 있어요. 앞으로도 잘 챙겨주세요!";
    }

    private String buildRecommendMessage(AssessmentResult current) {
        String name = typeName(current.getTypeKey());
        return name + " 결과가 중간 수준을 유지하고 있습니다. 꾸준한 관리를 위해 맞춤 추천 콘텐츠를 확인해 보세요.";
    }

    private String buildHighRiskMessage(AssessmentResult current) {
        String name = typeName(current.getTypeKey());
        return name + " 검사 결과가 마음에 걸려요. 혼자 버티기보다 도움을 받아보는 것도 괜찮아요. 상담소 찾기나 AI 케어를 통해 가볍게 시작해 보세요.";
    }

    private String buildMessage(AssessmentResult prev, AssessmentResult current) {
        String name = typeName(current.getTypeKey());
        if ("burnout".equals(current.getTypeKey())) {
            return name + " 검사 결과를 보니 요즘 많이 지쳐 있는 것 같아요. 잠깐 멈추고 나를 돌봐주는 시간을 가져보는 건 어떨까요?";
        }
        return name + " 검사 결과가 [" + prev.getLevel() + "] → [" + current.getLevel()
            + "](으)로 변화가 있었어요. 도움을 요청해보는 것도 괜찮아요. 부담 없이 가까운 상담을 찾아보세요.";
    }

    private String typeName(String typeKey) {
        return switch (typeKey) {
            case "depression" -> "우울증 (PHQ-9)";
            case "anxiety"    -> "불안장애 (GAD-7)";
            case "stress"     -> "스트레스 (PSS-10)";
            case "burnout"    -> "번아웃 (CBI)";
            default           -> typeKey;
        };
    }

    /** 최근 30일 이내 고위험 결과 존재 여부 */
    public boolean hasRecentHighRisk(User user) {
        return resultRepo.existsByUserAndHighRiskTrueAndCompletedAtAfter(
            user, LocalDateTime.now().minusDays(30));
    }

    public long countUnread(User user) {
        return alertRepo.countByUserAndReadFalse(user);
    }

    public List<UserAlert> getAllAlerts(User user) {
        return alertRepo.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public void markAllRead(User user) {
        alertRepo.findByUserAndReadFalseOrderByCreatedAtDesc(user)
            .forEach(a -> a.setRead(true));
    }

    @Transactional
    public void deleteAlert(Long id, User user) {
        alertRepo.findByIdAndUser(id, user).ifPresent(alertRepo::delete);
    }

    @Transactional
    public void deleteAllAlerts(User user) {
        alertRepo.deleteByUser(user);
    }

    // ===== 관리자 전용 =====

    public List<UserAlert> getHighRiskAlertsForAdmin() {
        return alertRepo.findByAlertTypeOrderByCreatedAtDesc("HIGH_RISK");
    }

    @Transactional
    public void confirmHighRiskAlert(Long alertId) {
        alertRepo.findById(alertId).ifPresent(a -> a.setAdminConfirmed(true));
    }

    public long countUnconfirmedHighRisk() {
        return alertRepo.countByAlertTypeAndAdminConfirmedFalse("HIGH_RISK");
    }
}
