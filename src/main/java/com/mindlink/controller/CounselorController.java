package com.mindlink.controller;

import com.mindlink.domain.UserRole;
import com.mindlink.repository.AssessmentResultRepository;
import com.mindlink.repository.PostRepository;
import com.mindlink.repository.UserRepository;
import com.mindlink.service.CommunityService;
import com.mindlink.service.UserNotificationService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/counselor")
public class CounselorController {

    private final CommunityService communityService;
    private final UserService userService;
    private final UserNotificationService notificationService;
    private final AssessmentResultRepository assessmentResultRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public CounselorController(CommunityService communityService,
                               UserService userService,
                               UserNotificationService notificationService,
                               AssessmentResultRepository assessmentResultRepository,
                               PostRepository postRepository,
                               UserRepository userRepository) {
        this.communityService = communityService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.assessmentResultRepository = assessmentResultRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    // ===== 대시보드 =====

    @GetMapping({"", "/"})
    public String dashboard(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isCounselor(session)) return denied(ra);
        model.addAttribute("totalUsers", userRepository.countByRole(UserRole.USER));
        model.addAttribute("highRiskCount", assessmentResultRepository.countByHighRiskTrue());
        model.addAttribute("postCount", postRepository.count());
        model.addAttribute("recentHighRisk",
                assessmentResultRepository.findTop5ByHighRiskTrueOrderByCompletedAtDesc());
        return "counselor/dashboard";
    }

    // ===== 게시글 관리 =====

    @GetMapping("/posts")
    public String posts(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isCounselor(session)) return denied(ra);
        model.addAttribute("posts", communityService.findAll(null));
        return "counselor/posts";
    }

    @PostMapping("/posts/{id}/delete")
    public String postDelete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        if (!isCounselor(session)) return denied(ra);
        communityService.adminDeletePost(id);
        ra.addFlashAttribute("flash", "게시글이 삭제되었습니다.");
        return "redirect:/counselor/posts";
    }

    // ===== 고위험도 관리 =====

    @GetMapping("/high-risk")
    public String highRisk(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isCounselor(session)) return denied(ra);
        model.addAttribute("results",
                assessmentResultRepository.findByHighRiskTrueOrderByCompletedAtDesc());
        return "counselor/high-risk";
    }

    @PostMapping("/high-risk/alert")
    public String sendHighRiskAlert(@RequestParam Long userId,
                                    @RequestParam(required = false) String title,
                                    @RequestParam String message,
                                    HttpSession session,
                                    RedirectAttributes ra) {
        if (!isCounselor(session)) return denied(ra);
        try {
            notificationService.sendAdminMessage(userId, title, message, false, null);
            ra.addFlashAttribute("flash", "해당 회원에게 알림을 보냈습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flash", e.getMessage());
        }
        return "redirect:/counselor/high-risk";
    }

    // ===== 알림 발송 =====

    @GetMapping("/alerts")
    public String alertsForm(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isCounselor(session)) return denied(ra);
        model.addAttribute("users", userService.findAll());
        return "counselor/alerts";
    }

    @PostMapping("/alerts")
    public String sendAlert(@RequestParam(required = false) Long userId,
                            @RequestParam(required = false) String title,
                            @RequestParam String message,
                            @RequestParam(required = false, defaultValue = "false") boolean includeLink,
                            @RequestParam(required = false) String linkUrl,
                            HttpSession session,
                            RedirectAttributes ra) {
        if (!isCounselor(session)) return denied(ra);
        try {
            int n = notificationService.sendAdminMessage(userId, title, message, includeLink, linkUrl);
            ra.addFlashAttribute("flash", userId != null
                    ? "해당 회원에게 알림을 보냈습니다."
                    : n + "명에게 알림을 보냈습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flash", e.getMessage());
        }
        return "redirect:/counselor/alerts";
    }

    // ===== 공통 =====

    private String denied(RedirectAttributes ra) {
        ra.addFlashAttribute("flash", "상담사 이상 권한이 필요합니다.");
        return "redirect:/";
    }

    private boolean isCounselor(HttpSession session) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) return false;
        return userService.findById(uid)
                .map(u -> u.getRole() == UserRole.COUNSELOR)
                .orElse(false);
    }
}
