package com.mindlink.service;

import com.mindlink.domain.ActivityLog;
import com.mindlink.domain.User;
import com.mindlink.repository.ActivityLogRepository;
import com.mindlink.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 추천 활동 수행 기록 저장 및 조회.
 * Phase 1: 기록(저장 + 일지). Phase 2: 연속일/주간 통계가 여기서 파생된다.
 */
@Service
@Transactional
public class ActivityService {

    /** 저장 가능한 활동 키 (recommendations.html 의 data-activity 와 1:1) */
    private static final Set<String> VALID_KEYS = Set.of(
            "breathing", "slow_breathing", "gratitude", "pmr_mini",
            "self_compassion_script", "checkin", "pmr", "bodyscan", "grounding",
            "thought_dump", "butterfly_hug", "small_action");

    /** 활동 키 → 한글 표시명 */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("breathing",              "호흡 운동"),
            Map.entry("slow_breathing",         "느린 호흡"),
            Map.entry("gratitude",              "감사 일기"),
            Map.entry("pmr_mini",               "PMR 미니"),
            Map.entry("self_compassion_script", "자기자비 문장"),
            Map.entry("checkin",                "감정 체크인"),
            Map.entry("pmr",                    "점진적 근육 이완"),
            Map.entry("bodyscan",               "짧은 바디스캔"),
            Map.entry("grounding",              "5-4-3-2-1 그라운딩"),
            Map.entry("thought_dump",           "생각 적어내기"),
            Map.entry("butterfly_hug",          "나비 포옹"),
            Map.entry("small_action",           "작은 실천 한 가지"));

    /** 활동 키 → 소요시간·분류 메타 (실행 페이지 부제) */
    private static final Map<String, String> META = Map.ofEntries(
            Map.entry("breathing",              "약 3분 · 신체 활동"),
            Map.entry("slow_breathing",         "약 3분 · 호흡"),
            Map.entry("gratitude",              "약 5분 · 기록 활동"),
            Map.entry("pmr_mini",               "약 5분 · 신체 이완"),
            Map.entry("self_compassion_script", "약 5분 · 인지"),
            Map.entry("checkin",                "약 2분 · 자기 인식"),
            Map.entry("pmr",                    "약 10분 · 신체 활동"),
            Map.entry("bodyscan",               "약 10분 · 명상"),
            Map.entry("grounding",              "약 3분 · 자기 인식"),
            Map.entry("thought_dump",           "약 5분 · 인지"),
            Map.entry("butterfly_hug",          "약 2분 · 정서 안정"),
            Map.entry("small_action",           "약 2분 · 행동 활성화"));

    private static final int PAYLOAD_MAX = 4000;

    private final ActivityLogRepository repo;
    private final UserRepository userRepo;

    public ActivityService(ActivityLogRepository repo, UserRepository userRepo) {
        this.repo = repo;
        this.userRepo = userRepo;
    }

    public boolean isValidKey(String key) {
        return key != null && VALID_KEYS.contains(key);
    }

    public String labelOf(String key) {
        return LABELS.getOrDefault(key, key);
    }

    public String metaOf(String key) {
        return META.getOrDefault(key, "");
    }

    /** 활동 1회 완료 기록. payload 는 직렬화된 JSON 문자열(없으면 null). */
    public ActivityLog log(Long userId, String activityKey, String payloadJson,
                           Integer moodScore, Integer durationSec, String programKey) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        if (!isValidKey(activityKey)) {
            throw new IllegalArgumentException("알 수 없는 활동입니다: " + activityKey);
        }
        ActivityLog log = new ActivityLog();
        log.setUser(user);
        log.setActivityKey(activityKey);
        if (payloadJson != null && payloadJson.length() > PAYLOAD_MAX) {
            payloadJson = payloadJson.substring(0, PAYLOAD_MAX);
        }
        log.setPayload(payloadJson);
        log.setMoodScore(moodScore);
        log.setDurationSec(durationSec);
        log.setProgramKey(programKey);
        return repo.save(log);
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> recent(Long userId) {
        return userRepo.findById(userId)
                .map(repo::findByUserOrderByCreatedAtDesc)
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public long countTotal(Long userId) {
        return userRepo.findById(userId).map(repo::countByUser).orElse(0L);
    }

    /** 이번 주(월요일 0시 이후) 수행 횟수 */
    @Transactional(readOnly = true)
    public long countThisWeek(Long userId) {
        LocalDateTime weekStart = LocalDate.now()
                .with(DayOfWeek.MONDAY).atStartOfDay();
        return userRepo.findById(userId)
                .map(u -> repo.countByUserAndCreatedAtAfter(u, weekStart))
                .orElse(0L);
    }
}
