package com.mindlink.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    @GetMapping("/")
    public String home() {
        return "home";
    }

    /** 구 /info → 홈 서비스 소개 섹션 */
    @GetMapping("/info")
    public String info() {
        return "redirect:/#service-intro";
    }

    /** 구 데모 채팅 → AI 위로 편지 위저드로 연결 */
    @GetMapping("/ai-care")
    public String aiCare() {
        return "redirect:/care-report/wizard";
    }

    @GetMapping("/recommendations")
    public String recommendations(
            @RequestParam(defaultValue = "NORMAL") String emotion,
            Model model) {
        model.addAttribute("emotion", emotion);
        return "recommendations";
    }
}
