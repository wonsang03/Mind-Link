package com.mindlink.controller;

import com.mindlink.domain.User;
import com.mindlink.domain.UserRole;
import com.mindlink.service.AdminService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 상담사 뷰. 관리 대상 유저(상담 이력 또는 고위험 자가진단)의 상태를 조회만 합니다(읽기 전용).
 * 전체 제어는 관리자 뷰(/admin)에서 수행합니다.
 */
@Controller
@RequestMapping("/counselor")
public class CounselorController {

    private final UserService userService;
    private final AdminService adminService;

    public CounselorController(UserService userService, AdminService adminService) {
        this.userService = userService;
        this.adminService = adminService;
    }

    @GetMapping
    public String dashboard(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isCounselor(session)) {
            ra.addFlashAttribute("flash", "상담사만 접근할 수 있습니다.");
            return "redirect:/";
        }
        model.addAttribute("managedUsers", adminService.findManagedUsers());
        return "counselor/dashboard";
    }

    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable Long id, HttpSession session,
                             Model model, RedirectAttributes ra) {
        if (!isCounselor(session)) {
            ra.addFlashAttribute("flash", "상담사만 접근할 수 있습니다.");
            return "redirect:/";
        }
        User user = userService.findById(id).orElse(null);
        if (user == null) {
            ra.addFlashAttribute("flash", "존재하지 않는 유저입니다.");
            return "redirect:/counselor";
        }
        model.addAttribute("user", user);
        model.addAttribute("posts", adminService.postsByUser(user));
        model.addAttribute("assessments", adminService.assessmentsByUser(user));
        model.addAttribute("bookings", adminService.bookingsByUser(user));
        model.addAttribute("canEdit", false);
        model.addAttribute("backUrl", "/counselor");
        return "admin/user-detail";
    }

    private boolean isCounselor(HttpSession session) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) return false;
        return userService.findById(uid)
                .map(u -> u.getRole() == UserRole.COUNSELOR || u.getRole() == UserRole.ADMIN)
                .orElse(false);
    }
}
