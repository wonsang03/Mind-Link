package com.mindlink.controller;

import com.mindlink.domain.Post;
import com.mindlink.domain.User;
import com.mindlink.domain.UserRole;
import com.mindlink.service.AdminService;
import com.mindlink.service.CommunityService;
import com.mindlink.service.NoticeService;
import com.mindlink.service.SqlConsoleService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final CommunityService communityService;
    private final NoticeService noticeService;
    private final UserService userService;
    private final SqlConsoleService sqlConsoleService;

    public AdminController(AdminService adminService,
                          CommunityService communityService,
                          NoticeService noticeService,
                          UserService userService,
                          SqlConsoleService sqlConsoleService) {
        this.adminService = adminService;
        this.communityService = communityService;
        this.noticeService = noticeService;
        this.userService = userService;
        this.sqlConsoleService = sqlConsoleService;
    }

    // ===== 대시보드 =====

    @GetMapping
    public String dashboard(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        model.addAttribute("stats", adminService.stats());
        model.addAttribute("managedUsers", adminService.findManagedUsers());
        model.addAttribute("notices", noticeService.findAll());
        return "admin/dashboard";
    }

    // ===== 유저 관리 =====

    @GetMapping("/users")
    public String users(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        model.addAttribute("users", userService.findAll());
        model.addAttribute("roles", UserRole.values());
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable Long id, HttpSession session,
                             Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        User user = userService.findById(id).orElse(null);
        if (user == null) {
            ra.addFlashAttribute("flash", "존재하지 않는 유저입니다.");
            return "redirect:/admin/users";
        }
        populateUserDetail(model, user);
        model.addAttribute("canEdit", true);
        model.addAttribute("backUrl", "/admin/users");
        return "admin/user-detail";
    }

    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam String role,
                             HttpSession session, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        try {
            userService.changeRole(id, UserRole.valueOf(role));
            ra.addFlashAttribute("flash", "등급이 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flash", "등급 변경에 실패했습니다: " + e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        Object me = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (me instanceof Long myId && myId.equals(id)) {
            ra.addFlashAttribute("flash", "본인 계정은 삭제할 수 없습니다.");
            return "redirect:/admin/users";
        }
        try {
            adminService.deleteUser(id);
            ra.addFlashAttribute("flash", "계정이 삭제되었습니다.");
        } catch (Exception e) {
            ra.addFlashAttribute("flash", "삭제에 실패했습니다: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ===== 게시글 관리 =====

    @GetMapping("/posts")
    public String posts(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        model.addAttribute("posts", communityService.findAll(null));
        return "admin/posts";
    }

    @GetMapping("/posts/{id}/edit")
    public String postEditForm(@PathVariable Long id, HttpSession session,
                               Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        Post post = communityService.findById(id).orElse(null);
        if (post == null) {
            ra.addFlashAttribute("flash", "존재하지 않는 게시글입니다.");
            return "redirect:/admin/posts";
        }
        model.addAttribute("post", post);
        return "admin/post-edit";
    }

    @PostMapping("/posts/{id}/edit")
    public String postEdit(@PathVariable Long id,
                           @RequestParam String title, @RequestParam String content,
                           @RequestParam String category,
                           HttpSession session, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        communityService.adminUpdatePost(id, title, content, category);
        ra.addFlashAttribute("flash", "게시글이 수정되었습니다.");
        return "redirect:/admin/posts";
    }

    @PostMapping("/posts/{id}/delete")
    public String postDelete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        communityService.adminDeletePost(id);
        ra.addFlashAttribute("flash", "게시글이 삭제되었습니다.");
        return "redirect:/admin/posts";
    }

    // ===== SQL 콘솔 =====

    @GetMapping("/sql")
    public String sqlConsole(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        return "admin/sql";
    }

    @PostMapping("/sql")
    public String runSql(@RequestParam String sql, HttpSession session,
                         Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        model.addAttribute("sql", sql);
        model.addAttribute("result", sqlConsoleService.execute(sql));
        return "admin/sql";
    }

    // ===== 공통 =====

    private void populateUserDetail(Model model, User user) {
        model.addAttribute("user", user);
        model.addAttribute("posts", adminService.postsByUser(user));
        model.addAttribute("assessments", adminService.assessmentsByUser(user));
        model.addAttribute("bookings", adminService.bookingsByUser(user));
    }

    private String denied(RedirectAttributes ra) {
        ra.addFlashAttribute("flash", "관리자만 접근할 수 있습니다.");
        return "redirect:/";
    }

    private boolean isAdmin(HttpSession session) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) return false;
        return userService.findById(uid)
                .map(u -> u.getRole() == UserRole.ADMIN)
                .orElse(false);
    }
}
