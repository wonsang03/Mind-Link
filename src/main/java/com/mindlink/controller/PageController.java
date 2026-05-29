package com.mindlink.controller;

import com.mindlink.service.ProverbService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    private final ProverbService proverbService;

    public PageController(ProverbService proverbService) {
        this.proverbService = proverbService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("proverb", proverbService.getRandom("HOME"));
        return "home";
    }

    @GetMapping("/info")
    public String info() {
        return "redirect:/#service-intro";
    }

    @GetMapping("/ai-care")
    public String aiCare() {
        return "redirect:/care-report/wizard";
    }

    @GetMapping("/recommendations")
    public String recommendations(
            @RequestParam(defaultValue = "NORMAL") String emotion,
            Model model) {
        model.addAttribute("emotion", emotion);
        model.addAttribute("proverb", proverbService.getRandom("RECOMMENDATIONS"));
        return "recommendations";
    }
}
