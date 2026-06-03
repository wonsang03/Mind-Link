package com.mindlink.controller;

import com.mindlink.service.CommunityCategoryPreferenceService;
import com.mindlink.service.CommunityService;
import com.mindlink.service.NoticeService;
import com.mindlink.service.ProverbService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    private final ProverbService proverbService;
    private final NoticeService noticeService;
    private final CommunityService communityService;
    private final CommunityCategoryPreferenceService categoryPreferenceService;

    public PageController(ProverbService proverbService,
                          NoticeService noticeService,
                          CommunityService communityService,
                          CommunityCategoryPreferenceService categoryPreferenceService) {
        this.proverbService = proverbService;
        this.noticeService = noticeService;
        this.communityService = communityService;
        this.categoryPreferenceService = categoryPreferenceService;
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
            @RequestParam(required = false) String emotion,
            HttpSession session,
            Model model) {
        // 감정을 명시하지 않고 들어오면(링크 등) 로그인 사용자의 우세 감정으로 기본 설정한다.
        String resolved = (emotion != null && !emotion.isBlank()) ? emotion : null;
        Object uid = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (resolved == null || "NORMAL".equalsIgnoreCase(resolved)) {
            if (uid instanceof Long id) {
                String dominant = categoryPreferenceService.resolveDominantEmotion(id);
                if (dominant != null) resolved = dominant;
            }
        }
        if (resolved == null || resolved.isBlank()) resolved = "NORMAL";
        model.addAttribute("emotion", resolved);

        // 맞춤순 정렬용 — 로그인 + 프로필 있으면 3축 norm 전달
        if (uid instanceof Long id) {
            java.util.Map<String, Double> norms = categoryPreferenceService.resolveEmotionNorms(id);
            if (!norms.isEmpty()) {
                model.addAttribute("stressNorm", norms.get("STRESS"));
                model.addAttribute("depressionNorm", norms.get("DEPRESSION"));
                model.addAttribute("anxietyNorm", norms.get("ANXIETY"));
            }
        }
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
