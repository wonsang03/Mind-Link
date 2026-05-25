package com.mindlink.care;

import com.mindlink.domain.AssessmentChoice;
import com.mindlink.domain.AssessmentQuestion;
import com.mindlink.domain.AssessmentType;
import com.mindlink.domain.ScoreRange;
import com.mindlink.domain.User;
import com.mindlink.service.AssessmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AI 종합 보고서(위로 편지) 위저드 파이프라인.
 * 1) 입력 검증 → 2) 자가진단 채점 → 3) 정보 종합 → 4) 입력 안전 필터 →
 * 5) OpenAI 편지 생성 → 6) 출력 안전 필터 + fallback → 7) DB 저장.
 * 한도: 같은 사용자 24시간 내 N 회 (기본 3).
 */
@Service
public class CareReportService {

    private static final Logger log = LoggerFactory.getLogger(CareReportService.class);

    private static final List<String> OPTIONAL_ASSESSMENTS = List.of("stress", "depression", "anxiety");

    private static final Set<String> HIGH_RISK_LEVELS = Set.of(
            "고위험군", "중증", "중등도-중증", "높음", "심함", "심한 우울", "심한 불안");

    private final AssessmentService assessmentService;
    private final CareContextAggregator aggregator;
    private final CareSafetyFilter safetyFilter;
    private final CareLetterService letterService;
    private final CareReportRepository reportRepository;

    @Value("${care-report.daily-limit:3}")
    private int dailyLimit;

    public CareReportService(AssessmentService assessmentService,
                              CareContextAggregator aggregator,
                              CareSafetyFilter safetyFilter,
                              CareLetterService letterService,
                              CareReportRepository reportRepository) {
        this.assessmentService = assessmentService;
        this.aggregator = aggregator;
        this.safetyFilter = safetyFilter;
        this.letterService = letterService;
        this.reportRepository = reportRepository;
    }

    @Transactional
    public GenerationResult generate(User user, CareReportDtos.GenerateRequest req) {
        if (user == null) {
            throw new IllegalStateException("로그인이 필요합니다.");
        }
        if (req == null) {
            throw new ValidationException("입력이 비어 있어요. 위저드를 다시 진행해 주세요.");
        }
        validateRequired(req);

        LocalDateTime since = LocalDateTime.now().minusDays(1);
        long recentCount = reportRepository.countByUserIdAndCreatedAtAfter(user.getId(), since);
        if (recentCount >= dailyLimit) {
            throw new RateLimitedException(
                    "하루에 최대 " + dailyLimit + "회까지 보고서를 받을 수 있어요. 잠시 후 다시 시도해 주세요.");
        }

        // 입력 정제 — 안전 필터를 통과시킨 값만 AI 에 전달
        String safeMood = safetyFilter.sanitizeUserInput(req.mood(), 300);
        String safeHardship = safetyFilter.sanitizeUserInput(req.recentHardship(), 700);
        String safeConcern = safetyFilter.sanitizeUserInput(req.concern(), 700);
        String safeComfort = safetyFilter.sanitizeUserInput(req.smallComfort(), 500);
        String safeHope = safetyFilter.sanitizeUserInput(req.hopeForward(), 500);
        String safeMessage = safetyFilter.sanitizeUserInput(req.oneLineMessage(), 400);

        // 완료된 자가진단만 채점
        List<CareContextAggregator.AssessmentScore> scores =
                scoreCompletedAssessments(req.assessments());

        // 정보 종합 → AI 스냅샷
        CareContextAggregator.WizardInput wizardInput = new CareContextAggregator.WizardInput(
                safeMood, safeHardship, safeConcern, safeComfort, safeHope, safeMessage);
        CareContextAggregator.Snapshot snapshot = aggregator.collect(user, wizardInput, scores);
        CareReport.RiskLevel risk = snapshot.riskLevel();

        // LLM 호출
        CareLetterService.GenerateResult aiResult =
                letterService.generateLetter(snapshot.snapshotJson(), risk);

        // 출력 필터 + fallback
        String finalLetter;
        List<String> themes;
        boolean usedFallback;
        if (aiResult.isSuccess()) {
            CareLetterService.LetterDraft draft = aiResult.draft().orElseThrow();
            CareSafetyFilter.LetterReview review =
                    safetyFilter.reviewGeneratedLetter(draft.letterBody(), risk);
            if (review.rejected() || review.sanitizedLetter() == null) {
                finalLetter = buildFallbackLetter(snapshot, scores, risk,
                        "안전 필터에 의해 일부 표현이 차단되어 기본 위로 메시지로 대체했어요.");
                themes = List.of();
                usedFallback = true;
            } else {
                finalLetter = review.sanitizedLetter();
                themes = draft.themes();
                usedFallback = false;
            }
        } else {
            finalLetter = buildFallbackLetter(snapshot, scores, risk, resolveFallbackReason(aiResult));
            themes = List.of();
            usedFallback = true;
        }

        CareReport report = new CareReport();
        report.setUserId(user.getId());
        report.setSnapshotJson(snapshot.snapshotJson());
        report.setLetterBody(finalLetter);
        report.setRiskLevel(risk);
        report.setThemes(joinThemes(themes));
        CareReport saved = reportRepository.save(report);

        log.info("CareReport generated userId={} reportId={} risk={} fallback={}",
                user.getId(), saved.getId(), risk, usedFallback);

        return new GenerationResult(saved, themes, usedFallback);
    }

    public List<CareReport> myList(Long userId) {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Optional<CareReport> findMine(Long userId, Long reportId) {
        return reportRepository.findByIdAndUserId(reportId, userId);
    }

    public static List<String> splitThemes(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 검증·채점 헬퍼
    // ─────────────────────────────────────────────────────────────────────

    private void validateRequired(CareReportDtos.GenerateRequest req) {
        if (blank(req.mood())) throw new ValidationException("「오늘의 기분」을 입력해 주세요.");
        if (blank(req.recentHardship())) throw new ValidationException("「최근 가장 힘든 일」을 입력해 주세요.");
        if (blank(req.concern())) throw new ValidationException("「요즘 가장 큰 고민」을 입력해 주세요.");
        if (blank(req.smallComfort())) throw new ValidationException("「잠깐이라도 위로가 됐던 순간」을 입력해 주세요.");
        if (blank(req.hopeForward())) throw new ValidationException("「앞으로 바라는 한 가지」를 입력해 주세요.");
    }

    private static boolean isCompleteAnswers(List<Integer> answers, int questionCount) {
        if (answers == null || questionCount <= 0) return false;
        for (int i = 0; i < questionCount; i++) {
            if (i >= answers.size()) return false;
            if (answers.get(i) == null) return false;
        }
        return true;
    }

    private List<CareContextAggregator.AssessmentScore> scoreCompletedAssessments(Map<String, List<Integer>> answers) {
        if (answers == null || answers.isEmpty()) {
            return List.of();
        }
        List<CareContextAggregator.AssessmentScore> scored = new ArrayList<>();
        for (String typeKey : OPTIONAL_ASSESSMENTS) {
            if (!answers.containsKey(typeKey)) continue;

            AssessmentType type = assessmentService.findByTypeKey(typeKey).orElse(null);
            if (type == null) continue;

            List<Integer> userAnswers = answers.get(typeKey);
            List<AssessmentQuestion> questions = type.getQuestions();
            List<AssessmentChoice> choices = type.getChoices();
            if (questions.isEmpty() || choices.isEmpty()) continue;
            if (!isCompleteAnswers(userAnswers, questions.size())) continue;

            int maxChoiceScore = choices.stream().mapToInt(AssessmentChoice::getScore).max().orElse(0);
            int total = 0;
            for (int i = 0; i < questions.size(); i++) {
                Integer answerVal = userAnswers.get(i);
                if (answerVal == null) {
                    throw new ValidationException(
                            "자가진단 「" + typeKey + "」 " + (i + 1) + "번 문항 답변이 비어 있어요.");
                }
                int choiceIdx = resolveChoiceIndex(answerVal, choices);
                int raw = choices.get(choiceIdx).getScore();
                total += questions.get(i).isReversed() ? (maxChoiceScore - raw) : raw;
            }

            int totalMax = questions.size() * maxChoiceScore;
            ScoreRange range = assessmentService.evaluate(type, total);
            String level = range != null ? range.getLevel() : "";
            boolean highRisk = level != null && HIGH_RISK_LEVELS.contains(level);

            scored.add(new CareContextAggregator.AssessmentScore(
                    type.getTypeKey(), type.getName(), total, totalMax, level, highRisk));
        }
        return scored;
    }

    /** 위저드는 선택지 인덱스(0-based)를 보낸다. 과거 payload 가 점수로 오면 score 로 매칭. */
    private static int resolveChoiceIndex(int answerVal, List<AssessmentChoice> choices) {
        if (answerVal >= 0 && answerVal < choices.size()) {
            return answerVal;
        }
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).getScore() == answerVal) {
                return i;
            }
        }
        throw new ValidationException("자가진단 답변 형식이 올바르지 않아요. 위저드에서 해당 검사를 다시 진행해 주세요.");
    }

    private static String joinThemes(List<String> themes) {
        if (themes == null || themes.isEmpty()) return null;
        Set<String> seen = new LinkedHashSet<>();
        StringBuilder sb = new StringBuilder();
        for (String t : themes) {
            String s = t == null ? "" : t.replace(',', ' ').trim();
            if (s.isBlank() || !seen.add(s)) continue;
            if (s.length() > 80) s = s.substring(0, 80);
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
            if (sb.length() >= 400) break;
        }
        return sb.toString();
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String resolveFallbackReason(CareLetterService.GenerateResult aiResult) {
        if (aiResult.userHint() != null && !aiResult.userHint().isBlank()) {
            return aiResult.userHint() + " 기본 위로 메시지를 보여드려요.";
        }
        return switch (aiResult.failureKind()) {
            case DISABLED -> "AI API 키가 설정되지 않아 기본 위로 메시지를 보여드려요.";
            case QUOTA_EXCEEDED ->
                    "AI API 한도 또는 잔액 문제로 편지를 만들지 못했어요. 기본 위로 메시지를 보여드려요.";
            case PARSE_ERROR ->
                    "AI 응답 형식이 올바르지 않아 기본 위로 메시지를 보여드려요.";
            default -> "AI 응답을 받지 못해 기본 위로 메시지를 보여드려요.";
        };
    }

    /** AI 가 실패하거나 출력이 차단됐을 때 사용하는 기본 편지 */
    private String buildFallbackLetter(CareContextAggregator.Snapshot snapshot,
                                        List<CareContextAggregator.AssessmentScore> scores,
                                        CareReport.RiskLevel risk, String reason) {
        StringBuilder sb = new StringBuilder();
        if (risk == CareReport.RiskLevel.CRISIS) {
            sb.append(CareSafetyFilter.CRISIS_SUPPORT_MESSAGE);
        }
        sb.append(snapshot.nickname()).append("님께,\n\n");
        sb.append("오늘 위저드를 끝까지 진행해 주셔서 고마워요. ")
          .append("그 자체가 자신을 돌아보려는 큰 시도예요.\n\n");

        if (!scores.isEmpty()) {
            sb.append("자가진단을 함께 살펴본 결과는 이래요: ");
            for (int i = 0; i < scores.size(); i++) {
                CareContextAggregator.AssessmentScore s = scores.get(i);
                if (i > 0) sb.append(", ");
                sb.append(s.displayName()).append(" ").append(s.score()).append("/").append(s.maxScore());
                if (s.level() != null && !s.level().isBlank()) sb.append("(").append(s.level()).append(")");
            }
            sb.append(".\n\n");
            sb.append("결과는 진단이 아니라 신호예요. ");
        } else {
            sb.append("오늘 적어 주신 이야기를 바탕으로 마음을 돌아봤어요. ");
        }

        sb.append("오늘만은 평소보다 조금 더 자신에게 너그럽게 굴어 주세요. ")
          .append("따뜻한 물 한 잔, 평소보다 30분 일찍 자기, 좋아하는 음악 한 곡 듣기 같은 작은 행동이면 충분해요.\n\n");

        if (risk == CareReport.RiskLevel.ELEVATED) {
            sb.append("요즘 마음이 조금 무거우신 것 같아요. 혼자 견디기 어렵다면 ")
              .append("상담소 찾기 메뉴에서 가까운 전문가에게 한 번 도움을 청해 보는 것도 좋은 선택이에요.\n\n");
        }
        sb.append("당신은 이미 충분히 잘 버티고 있어요. 내일 다시 이야기 나눌 수 있길 바라요.\n");
        sb.append("\n— 마음이음 드림\n");
        sb.append("\n(안내: ").append(reason).append(")");
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────
    // 예외 / 결과
    // ─────────────────────────────────────────────────────────────────────

    public static class ValidationException extends RuntimeException {
        public ValidationException(String msg) { super(msg); }
    }

    public static class RateLimitedException extends RuntimeException {
        public RateLimitedException(String msg) { super(msg); }
    }

    public record GenerationResult(CareReport report, List<String> themes, boolean usedFallback) {}
}
