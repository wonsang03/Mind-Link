package com.mindlink.controller;

import com.mindlink.domain.User;
import com.mindlink.dto.DiagnosisResultResponse;
import com.mindlink.service.DiagnosisService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/self-assessment")
public class DiagnosisController {

    private static final List<Map<String, Object>> TESTS = List.of(
            Map.of("id", "depression", "title", "우울증 자가진단",
                    "description", "PHQ-9 기반 우울증 선별 검사",
                    "duration", "약 3분", "questions", 9),
            Map.of("id", "anxiety", "title", "불안장애 자가진단",
                    "description", "GAD-7 기반 불안 수준 평가",
                    "duration", "약 2분", "questions", 7),
            Map.of("id", "stress", "title", "스트레스 자가진단",
                    "description", "PSS 기반 스트레스 측정",
                    "duration", "약 5분", "questions", 10),
            Map.of("id", "burnout", "title", "번아웃 자가진단",
                    "description", "업무 소진 정도 측정",
                    "duration", "약 4분", "questions", 12)
    );

    private static final List<String> QUESTIONS = List.of(
            "일상생활에 흥미나 즐거움을 느끼지 못했다",
            "기분이 가라앉거나 우울하거나 희망이 없다고 느꼈다",
            "잠들기 어렵거나 자주 깨거나 너무 많이 잤다",
            "피곤하고 기운이 없다고 느꼈다",
            "식욕이 없거나 과식을 했다"
    );

    private final DiagnosisService diagnosisService;
    private final UserService userService;

    public DiagnosisController(DiagnosisService diagnosisService, UserService userService) {
        this.diagnosisService = diagnosisService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tests", TESTS);
        return "self-assessment/list";
    }

    @GetMapping("/{testId}")
    public String quiz(@PathVariable String testId, Model model) {
        Map<String, Object> test = TESTS.stream()
                .filter(t -> testId.equals(t.get("id")))
                .findFirst().orElse(TESTS.get(0));
        model.addAttribute("test", test);
        model.addAttribute("questions", QUESTIONS);
        return "self-assessment/quiz";
    }

    @PostMapping("/{testId}/result")
    public String result(@PathVariable String testId,
                         @RequestParam(name = "answer", required = false) List<Integer> answers,
                         HttpSession session,
                         Model model) {
        // 로그인 사용자 확인 (비로그인도 허용)
        User currentUser = null;
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (userId instanceof Long uid) {
            currentUser = userService.findById(uid).orElse(null);
        }

        Map<String, Object> test = TESTS.stream()
                .filter(t -> testId.equals(t.get("id")))
                .findFirst().orElse(TESTS.get(0));

        DiagnosisResultResponse result = diagnosisService.evaluate(testId, answers, currentUser);

        model.addAttribute("test", test);
        model.addAttribute("score", result.getScore());
        model.addAttribute("level", result.getLevel());
        model.addAttribute("message", result.getMessage());
        model.addAttribute("highRisk", result.isHighRisk());
        return "self-assessment/result";
    }
}
