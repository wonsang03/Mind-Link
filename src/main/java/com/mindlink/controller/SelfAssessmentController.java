package com.mindlink.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/self-assessment")
public class SelfAssessmentController {

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

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tests", TESTS);
        return "self-assessment/list";
    }

    @GetMapping("/{testId}")
    public String quiz(@org.springframework.web.bind.annotation.PathVariable String testId, Model model) {
        Map<String, Object> test = TESTS.stream()
                .filter(t -> testId.equals(t.get("id")))
                .findFirst().orElse(TESTS.get(0));
        model.addAttribute("test", test);
        model.addAttribute("questions", QUESTIONS);
        return "self-assessment/quiz";
    }

    @PostMapping("/{testId}/result")
    public String result(@org.springframework.web.bind.annotation.PathVariable String testId,
                         @RequestParam(name = "answer", required = false) List<Integer> answers,
                         Model model) {
        int score = answers == null ? 0 : answers.stream().mapToInt(Integer::intValue).sum();

        String level;
        String message;
        if (score <= 4) {
            level = "정상";
            message = "현재 우울 증상이 거의 없는 상태입니다.";
        } else if (score <= 9) {
            level = "경미";
            message = "가벼운 우울 증상이 있습니다. 자기 관리에 신경 써주세요.";
        } else if (score <= 14) {
            level = "중등도";
            message = "중간 정도의 우울 증상이 있습니다. 전문가 상담을 고려해보세요.";
        } else {
            level = "심각";
            message = "심한 우울 증상이 있습니다. 전문가의 도움이 필요합니다.";
        }

        Map<String, Object> test = TESTS.stream()
                .filter(t -> testId.equals(t.get("id")))
                .findFirst().orElse(TESTS.get(0));

        model.addAttribute("test", test);
        model.addAttribute("score", score);
        model.addAttribute("level", level);
        model.addAttribute("message", message);
        model.addAttribute("highRisk", score >= 10);
        return "self-assessment/result";
    }
}
