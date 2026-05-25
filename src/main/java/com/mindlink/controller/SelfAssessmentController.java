package com.mindlink.controller;

import com.mindlink.domain.AssessmentQuestion;
import com.mindlink.domain.AssessmentType;
import com.mindlink.domain.ScoreRange;
import com.mindlink.domain.User;
import com.mindlink.domain.UserAlert;
import com.mindlink.chatcluster.ClusterProfileService;
import com.mindlink.service.AssessmentService;
import com.mindlink.service.MonitoringService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/self-assessment")
public class SelfAssessmentController {

    private final AssessmentService assessmentService;
    private final MonitoringService monitoringService;
    private final ClusterProfileService clusterProfileService;
    private final UserService userService;

    public SelfAssessmentController(AssessmentService assessmentService,
                                     MonitoringService monitoringService,
                                     ClusterProfileService clusterProfileService,
                                     UserService userService) {
        this.assessmentService  = assessmentService;
        this.monitoringService  = monitoringService;
        this.clusterProfileService = clusterProfileService;
        this.userService        = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("assessments", assessmentService.findAll());
        return "self-assessment/list";
    }

    @GetMapping("/{typeKey}")
    public String quiz(@PathVariable String typeKey, Model model) {
        AssessmentType assessment = assessmentService.findByTypeKey(typeKey)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 검사입니다."));
        model.addAttribute("assessment", assessment);
        if ("burnout".equals(typeKey)) {
            Set<Integer> partStartIndices = new HashSet<>();
            List<AssessmentQuestion> questions = assessment.getQuestions();
            int prevPart = -1;
            for (int i = 0; i < questions.size(); i++) {
                int p = questions.get(i).getPart();
                if (p != prevPart) {
                    partStartIndices.add(i);
                    prevPart = p;
                }
            }
            model.addAttribute("partStartIndices", partStartIndices);
        }
        return "self-assessment/quiz";
    }

    @PostMapping("/{typeKey}/result")
    public String result(@PathVariable String typeKey,
                         @RequestParam Map<String, String> params,
                         HttpSession session,
                         Model model) {
        AssessmentType assessment = assessmentService.findByTypeKey(typeKey).orElseThrow();
        List<AssessmentQuestion> questions = assessment.getQuestions();
        int maxChoiceScore = assessment.getChoices().stream()
                .mapToInt(c -> c.getScore())
                .max().orElse(0);

        if ("burnout".equals(typeKey)) {
            return burnoutResult(assessment, questions, params, maxChoiceScore, session, model);
        }

        int totalScore = 0;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!entry.getKey().startsWith("q")) continue;
            int idx = Integer.parseInt(entry.getKey().substring(1));
            int raw = Integer.parseInt(entry.getValue());
            totalScore += questions.get(idx).isReversed() ? (maxChoiceScore - raw) : raw;
        }
        ScoreRange range = assessmentService.evaluate(assessment, totalScore);
        String level = range.getLevel();
        boolean highRisk = "고위험군".equals(level) || "중등도-중증".equals(level) || "중증".equals(level) || "높음".equals(level);

        // 로그인 사용자 → 결과 저장 + 악화 감지
        User loginUser = getLoginUser(session);
        if (loginUser != null) {
            UserAlert alert = monitoringService.saveAndMonitor(
                loginUser, typeKey, totalScore, level, highRisk,
                null, null, null, null);
            clusterProfileService.mergeAssessmentScore(
                    loginUser.getId(), loginUser.getName(), typeKey, totalScore);
            if (alert != null) model.addAttribute("resultAlert", alert);
        } else {
            model.addAttribute("saveResultHint",
                    "로그인 후 검사하면 결과가 저장되고, 고위험·악화 시 알림(상단 종 아이콘)을 받을 수 있습니다.");
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("score", totalScore);
        model.addAttribute("level", level);
        model.addAttribute("message", range.getMessage());
        model.addAttribute("highRisk", highRisk);
        return "self-assessment/result";
    }

    private User getLoginUser(HttpSession session) {
        Object id = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (id instanceof Long uid) return userService.findById(uid).orElse(null);
        return null;
    }

    private String burnoutResult(AssessmentType assessment, List<AssessmentQuestion> questions,
                                  Map<String, String> params, int maxChoiceScore,
                                  HttpSession session, Model model) {
        int part1Sum = 0, part1Count = 0, part2Sum = 0, part2Count = 0;
        for (AssessmentQuestion q : questions) {
            if (q.getPart() == 1) part1Count++; else part2Count++;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!entry.getKey().startsWith("q")) continue;
            int idx = Integer.parseInt(entry.getKey().substring(1));
            int raw = Integer.parseInt(entry.getValue());
            AssessmentQuestion q = questions.get(idx);
            int scored = q.isReversed() ? (maxChoiceScore - raw) : raw;
            if (q.getPart() == 1) part1Sum += scored; else part2Sum += scored;
        }
        int personalScore = part1Count > 0 ? Math.round((float) part1Sum / part1Count) : 0;
        int workScore     = part2Count > 0 ? Math.round((float) part2Sum / part2Count) : 0;
        String personalLevel = burnoutLevel(personalScore);
        String workLevel     = burnoutLevel(workScore);
        boolean highRisk = "높음".equals(personalLevel) || "높음".equals(workLevel);

        // 로그인 사용자 → 결과 저장 + 악화 감지
        User loginUser = getLoginUser(session);
        if (loginUser != null) {
            UserAlert alert = monitoringService.saveAndMonitor(
                loginUser, assessment.getTypeKey(), null, null, highRisk,
                personalScore, personalLevel, workScore, workLevel);
            if (alert != null) model.addAttribute("resultAlert", alert);
        } else {
            model.addAttribute("saveResultHint",
                    "로그인 후 검사하면 결과가 저장되고, 고위험·악화 시 알림(상단 종 아이콘)을 받을 수 있습니다.");
        }

        model.addAttribute("assessment", assessment);
        model.addAttribute("isBurnout", true);
        model.addAttribute("personalScore", personalScore);
        model.addAttribute("workScore", workScore);
        model.addAttribute("personalLevel", personalLevel);
        model.addAttribute("workLevel", workLevel);
        model.addAttribute("message", burnoutMessage(personalLevel, workLevel));
        model.addAttribute("highRisk", highRisk);
        return "self-assessment/result";
    }

    private static String burnoutLevel(int score) {
        if (score < 50) return "낮음";
        if (score < 75) return "보통";
        return "높음";
    }

    private static String burnoutMessage(String personal, String work) {
        return switch (personal + "+" + work) {
            case "낮음+낮음" -> "현재 번아웃 위험이 낮은 상태입니다. 균형 잡힌 생활을 유지하고 계십니다.";
            case "낮음+보통" -> "업무에서 다소 소진이 느껴지고 있습니다. 충분한 휴식을 챙기시길 권장합니다.";
            case "낮음+높음" -> "개인적으로는 안정적이나 업무에서 오는 소진이 높습니다. 업무 환경 점검이 필요합니다.";
            case "보통+낮음" -> "전반적인 피로감이 다소 있으나 업무 적응은 양호한 상태입니다.";
            case "보통+보통" -> "개인과 업무 모두에서 중간 수준의 소진이 나타나고 있습니다. 관리가 필요한 시점입니다.";
            case "보통+높음" -> "업무로 인한 소진이 심화되고 있습니다. 적극적인 휴식과 업무 조정을 권장합니다.";
            case "높음+낮음" -> "업무 외 개인적인 영역에서 소진이 높습니다. 일상 회복과 자기 돌봄이 필요합니다.";
            case "높음+보통" -> "전반적인 소진이 상당한 수준입니다. 휴식과 주변의 지지가 필요합니다.";
            case "높음+높음" -> "개인과 업무 모두에서 번아웃 위험이 높습니다. 전문가 상담을 고려해 보시길 권장합니다.";
            default -> "";
        };
    }
}
