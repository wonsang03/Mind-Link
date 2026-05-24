package com.mindlink.care;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mindlink.domain.User;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 위저드에서 받은 입력만으로 AI 입력 스냅샷을 만든다.
 * 명세상 게시물·댓글·상담 통계·도서 추천 이력은 수집·표시·AI 입력 모두 금지이므로
 * 본 클래스는 DB 활동 데이터를 일절 참조하지 않는다.
 *
 * 포함 항목:
 *  - profile: 닉네임만
 *  - todayInput: 기분·힘든 일·고민·위로 순간·바라는 것·(선택) 한 마디
 *  - assessments: 선택 — 완료한 검사만 점수·구간
 */
@Component
public class CareContextAggregator {

    private static final int MAX_TOTAL_JSON_LEN = 8000;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CareSafetyFilter safetyFilter;
    private final ObjectMapper mapper = new ObjectMapper();

    public CareContextAggregator(CareSafetyFilter safetyFilter) {
        this.safetyFilter = safetyFilter;
    }

    /**
     * @param user       세션 사용자 (닉네임만 사용)
     * @param input      위저드에서 받은 입력 (mood/hardship/concern + 선택 message)
     * @param scores     완료한 자가진단만 (0~3건)
     */
    public Snapshot collect(User user, WizardInput input, List<AssessmentScore> scores) {
        if (input == null) {
            throw new IllegalArgumentException("위저드 입력은 필수입니다.");
        }
        if (scores == null) {
            scores = List.of();
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("generatedAt",
                LocalDateTime.now().atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        // 프로필 — 닉네임만 (가입일도 익명화 목적에서 제외)
        String nickname = safeName(user.getName());
        ObjectNode profile = root.putObject("profile");
        profile.put("nickname", nickname);
        profile.put("joinedAt",
                user.getCreatedAt() != null ? user.getCreatedAt().format(DATE_FMT) : "");

        // 위저드 입력 — PII 마스킹은 호출자(서비스)가 sanitizeUserInput 으로 이미 정제했음
        ObjectNode todayInput = root.putObject("todayInput");
        todayInput.put("mood", nullToEmpty(input.mood()));
        todayInput.put("recentHardship", nullToEmpty(input.recentHardship()));
        todayInput.put("concern", nullToEmpty(input.concern()));
        todayInput.put("smallComfort", nullToEmpty(input.smallComfort()));
        todayInput.put("hopeForward", nullToEmpty(input.hopeForward()));
        todayInput.put("oneLineMessage", nullToEmpty(input.oneLineMessage()));

        // 자가진단 — 검사명 + 점수 + 구간 라벨만 (문항 원문은 절대 포함하지 않는다)
        ArrayNode assessmentsArr = root.putArray("assessments");
        for (AssessmentScore s : scores) {
            ObjectNode node = assessmentsArr.addObject();
            node.put("type", s.typeKey());
            node.put("name", s.displayName());
            node.put("score", s.score());
            node.put("maxScore", s.maxScore());
            node.put("level", s.level());
            node.put("highRisk", s.highRisk());
        }

        // 위험 등급 — 사용자 자유 입력 + 자가진단 고위험을 함께 본다
        CareReport.RiskLevel risk = detectRiskLevel(input, scores);

        String json;
        try {
            json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            json = root.toString();
        }
        if (json.length() > MAX_TOTAL_JSON_LEN) {
            json = json.substring(0, MAX_TOTAL_JSON_LEN) + "\n...(잘림)";
        }
        return new Snapshot(json, risk, nickname);
    }

    private CareReport.RiskLevel detectRiskLevel(WizardInput input, List<AssessmentScore> scores) {
        List<String> texts = new ArrayList<>();
        texts.add(input.mood());
        texts.add(input.recentHardship());
        texts.add(input.concern());
        texts.add(input.smallComfort());
        texts.add(input.hopeForward());
        texts.add(input.oneLineMessage());
        CareReport.RiskLevel textRisk = safetyFilter.detectRiskLevel(texts.toArray(new String[0]));

        if (textRisk == CareReport.RiskLevel.CRISIS) return CareReport.RiskLevel.CRISIS;

        boolean anyHighRisk = scores.stream().anyMatch(AssessmentScore::highRisk);
        if (anyHighRisk) return CareReport.RiskLevel.ELEVATED;
        return textRisk;
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) return "당신";
        return name.length() <= 20 ? name : name.substring(0, 20);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 위저드 자유 입력 (sanitize 된 값) */
    public record WizardInput(String mood, String recentHardship, String concern,
                              String smallComfort, String hopeForward, String oneLineMessage) {}

    /**
     * 자가진단 1건 결과.
     * - typeKey: stress / depression / anxiety
     * - level: ScoreRange.level (정상/약간/중등도/심함 등)
     */
    public record AssessmentScore(String typeKey, String displayName,
                                   int score, int maxScore,
                                   String level, boolean highRisk) {}

    /** 정보 종합 결과 — letter 작성에 필요한 최소 정보 + DB 저장용 JSON */
    public record Snapshot(String snapshotJson, CareReport.RiskLevel riskLevel, String nickname) {}
}
