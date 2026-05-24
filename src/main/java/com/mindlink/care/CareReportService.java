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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AI 종합 보고서(위로 편지) 위저드 파이프라인.
 *
 * 순서:
 *   1) 입력 검증 (필수 자유 입력 5개, 자가진단은 선택)
 *   2) 자가진단 채점 (AssessmentService → 점수·구간 라벨)
 *   3) 정보 종합 (CareContextAggregator) — DB 활동 데이터는 일절 참조 안 함
 *   4) 보안 필터 — 입력 마스킹/위기 표현 차단은 Aggregator 호출 전에 적용
 *   5) Gemini 편지 생성
 *   6) 출력 필터 — 의료 진단·자해 조장·약물 처방 표현 제거
 *   7) DB 저장
 * 한도: 같은 사용자 24시간 내 N 회 (기본 3)
 */
@Service
public class CareReportService {

    private static final Logger log = LoggerFactory.getLogger(CareReportService.class);

    /** 위저드에서 선택 가능한 자가진단 종류 */
    private static final List<String> OPTIONAL_ASSESSMENTS = List.of("stress", "depression", "anxiety");

    /** 자가진단 결과가 '고위험' 수준이면 ELEVATED 로 분류할 라벨 (대소문자 무관) */
    private static final Set<String> HIGH_RISK_LEVELS = Set.of(
            "고위험군", "중증", "중등도-중증", "높음", "심함", "심한 우울", "심한 불안");

    private final AssessmentService assessmentService;
    private final CareContextAggregator aggregator;
    private final CareSafetyFilter safetyFilter;
    private final CareLetterAiRouter letterAi;
    private final CareReportRepository reportRepository;
    private final CareDailyInputRepository dailyInputRepository;

    @Value("${care-report.daily-limit:3}")
    private int dailyLimit;

    public CareReportService(AssessmentService assessmentService,
                              CareContextAggregator aggregator,
                              CareSafetyFilter safetyFilter,
                              CareLetterAiRouter letterAi,
                              CareReportRepository reportRepository,
                              CareDailyInputRepository dailyInputRepository) {
        this.assessmentService = assessmentService;
        this.aggregator = aggregator;
        this.safetyFilter = safetyFilter;
        this.letterAi = letterAi;
        this.reportRepository = reportRepository;
        this.dailyInputRepository = dailyInputRepository;
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

        // 한도 체크
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        long recentCount = reportRepository.countByUserIdAndCreatedAtAfter(user.getId(), since);
        if (recentCount >= dailyLimit) {
            throw new RateLimitedException(
                    "하루에 최대 " + dailyLimit + "회까지 보고서를 받을 수 있어요. 잠시 후 다시 시도해 주세요.");
        }

        // 1) 입력 정제 — 안전 필터를 통과시킨 값만 AI 에 전달
        String safeMood = safetyFilter.sanitizeUserInput(req.getMood(), 300);
        String safeHardship = safetyFilter.sanitizeUserInput(req.getRecentHardship(), 700);
        String safeConcern = safetyFilter.sanitizeUserInput(req.getConcern(), 700);
        String safeComfort = safetyFilter.sanitizeUserInput(req.getSmallComfort(), 500);
        String safeHope = safetyFilter.sanitizeUserInput(req.getHopeForward(), 500);
        String safeMessage = safetyFilter.sanitizeUserInput(req.getOneLineMessage(), 400);

        // 2) 자가진단 채점 (완료한 검사만)
        List<CareContextAggregator.AssessmentScore> scores =
                scoreCompletedAssessments(req.getAssessments());

        // 3) 정보 종합
        CareContextAggregator.WizardInput wizardInput = new CareContextAggregator.WizardInput(
                safeMood, safeHardship, safeConcern, safeComfort, safeHope, safeMessage);
        CareContextAggregator.Snapshot snapshot = aggregator.collect(user, wizardInput, scores);

        // 일일 입력 저장
        CareDailyInput entity = new CareDailyInput();
        entity.setUserId(user.getId());
        entity.setMood(notBlankOrNull(req.getMood(), 500));
        entity.setHardship(notBlankOrNull(req.getRecentHardship(), 1000));
        entity.setCurrentThought(notBlankOrNull(combineTextFields(
                req.getConcern(), req.getSmallComfort(), req.getHopeForward(), req.getOneLineMessage()), 1000));
        dailyInputRepository.save(entity);

        CareReport.RiskLevel risk = snapshot.riskLevel();

        // 5) LLM 호출 (care.llm.provider=openai 기본, .env OPENAI_API_KEY)
        CareLetterAiResult.GenerateResult aiResult =
                letterAi.generateLetter(snapshot.snapshotJson(), risk);

        // 6) 출력 필터 + fallback
        String finalLetter;
        List<String> themes;
        boolean usedFallback;
        if (aiResult.isSuccess()) {
            CareLetterAiResult.LetterDraft draft = aiResult.draft().orElseThrow();
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
            finalLetter = buildFallbackLetter(snapshot, scores, risk,
                    resolveFallbackReason(aiResult));
            themes = List.of();
            usedFallback = true;
        }

        // 7) 저장
        CareReport report = new CareReport();
        report.setUserId(user.getId());
        report.setSnapshotJson(snapshot.snapshotJson());
        report.setLetterBody(finalLetter);
        report.setRiskLevel(risk);
        report.setThemes(joinThemes(themes));
        CareReport saved = reportRepository.save(report);

        log.info("CareReport(wizard) generated userId={} reportId={} risk={} provider={} fallback={}",
                user.getId(), saved.getId(), risk, letterAi.activeProvider(), usedFallback);

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
        if (blank(req.getMood())) throw new ValidationException("「오늘의 기분」을 입력해 주세요.");
        if (blank(req.getRecentHardship())) throw new ValidationException("「최근 가장 힘든 일」을 입력해 주세요.");
        if (blank(req.getConcern())) throw new ValidationException("「요즘 가장 큰 고민」을 입력해 주세요.");
        if (blank(req.getSmallComfort())) throw new ValidationException("「잠깐이라도 위로가 됐던 순간」을 입력해 주세요.");
        if (blank(req.getHopeForward())) throw new ValidationException("「앞으로 바라는 한 가지」를 입력해 주세요.");
    }

    /** JSON·희소 배열에서 null 슬롯이 오는 경우를 막기 위한 완료 검사 */
    private static boolean isCompleteAnswers(List<Integer> answers, int questionCount) {
        if (answers == null || questionCount <= 0) return false;
        for (int i = 0; i < questionCount; i++) {
            if (i >= answers.size()) return false;
            Integer v = answers.get(i);
            if (v == null) return false;
        }
        return true;
    }

    /** 완료한 자가진단만 채점. 미완료·미응시 검사는 건너뜀 */
    private List<CareContextAggregator.AssessmentScore> scoreCompletedAssessments(Map<String, List<Integer>> answers) {
        if (answers == null || answers.isEmpty()) {
            return List.of();
        }
        List<CareContextAggregator.AssessmentScore> scored = new java.util.ArrayList<>();
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

    /**
     * 위저드는 선택지 인덱스(0-based)를 보낸다. 과거/오류 payload 에 점수 값이 오면 score 로 매칭한다.
     */
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

    private static String notBlankOrNull(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isBlank()) return null;
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String combineTextFields(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(p.trim());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String resolveFallbackReason(CareLetterAiResult.GenerateResult aiResult) {
        if (aiResult.userHint() != null && !aiResult.userHint().isBlank()) {
            return aiResult.userHint() + " 기본 위로 메시지를 보여드려요.";
        }
        return switch (aiResult.failureKind()) {
            case DISABLED -> "AI API 키가 설정되지 않아 기본 위로 메시지를 보여드려요.";
            case QUOTA_EXCEEDED ->
                    "AI API 한도 또는 잔액 문제로 편지를 만들지 못했어요. 기본 위로 메시지를 보여드려요.";
            case PARSE_ERROR, TOO_SHORT ->
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
    // 예외
    // ─────────────────────────────────────────────────────────────────────

    public static class ValidationException extends RuntimeException {
        public ValidationException(String msg) { super(msg); }
    }

    public static class RateLimitedException extends RuntimeException {
        public RateLimitedException(String msg) { super(msg); }
    }

    public record GenerationResult(CareReport report, List<String> themes, boolean usedFallback) {}
}
