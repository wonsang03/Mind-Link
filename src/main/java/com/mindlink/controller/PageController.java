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

    @GetMapping("/info")
    public String info() {
        return "info";
    }

    @GetMapping("/ai-care")
    public String aiCare() {
        return "ai-care";
    }

    @GetMapping("/recommendations")
    public String recommendations(
            @RequestParam(defaultValue = "NORMAL") String emotion,
            Model model) {
        model.addAttribute("emotion", emotion);
        return "recommendations";
    }
}
