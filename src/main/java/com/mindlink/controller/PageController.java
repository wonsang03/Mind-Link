package com.mindlink.controller;

import com.mindlink.service.CommunityService;
import com.mindlink.service.NoticeService;
import com.mindlink.service.ProverbService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    private final ProverbService proverbService;
    private final NoticeService noticeService;
    private final CommunityService communityService;

    public PageController(ProverbService proverbService,
                          NoticeService noticeService,
                          CommunityService communityService) {
        this.proverbService = proverbService;
        this.noticeService = noticeService;
        this.communityService = communityService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("proverb", proverbService.getRandom("HOME"));
        var allNotices = noticeService.findAll();
        model.addAttribute("notices", allNotices.size() > 5 ? allNotices.subList(0, 5) : allNotices);
        var allPosts = communityService.findAll(null);
        model.addAttribute("recentPosts", allPosts.size() > 5 ? allPosts.subList(0, 5) : allPosts);
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

    @GetMapping("/privacy")
    public String privacy(@RequestParam(defaultValue = "false") boolean embed, Model model) {
        if (embed) model.addAttribute("embed", true);
        return "privacy";
    }

    @GetMapping("/terms")
    public String terms(@RequestParam(defaultValue = "false") boolean embed, Model model) {
        if (embed) model.addAttribute("embed", true);
        return "terms";
    }
}
