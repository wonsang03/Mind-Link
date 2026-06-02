package com.mindlink.controller;

import com.mindlink.domain.Booking;
import com.mindlink.domain.Post;
import com.mindlink.domain.User;
import com.mindlink.domain.UserRole;
import com.mindlink.service.AdminMonitoringService;
import com.mindlink.service.AdminService;
import com.mindlink.service.CommunityService;
import com.mindlink.service.MonitoringService;
import com.mindlink.service.NoticeService;
import com.mindlink.service.SqlConsoleService;
import com.mindlink.service.UserNotificationService;
import com.mindlink.service.UserService;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final CommunityService communityService;
    private final NoticeService noticeService;
    private final UserService userService;
    private final SqlConsoleService sqlConsoleService;
    private final UserNotificationService notificationService;
    private final AdminMonitoringService adminMonitoringService;
    private final MonitoringService monitoringService;

    @Value("${chat.cluster.k:6}")
    private int k;
    @Value("${chat.cluster.weight.stress:1.0}")
    private double weightStress;
    @Value("${chat.cluster.weight.depression:1.2}")
    private double weightDepression;
    @Value("${chat.cluster.weight.anxiety:1.0}")
    private double weightAnxiety;
    @Value("${chat.cluster.seed.persona-types:30}")
    private int personaTypes;
    @Value("${chat.cluster.seed.variants-per-persona:7}")
    private int variantsPerPersona;

    public AdminController(AdminService adminService,
                           CommunityService communityService,
                           NoticeService noticeService,
                           UserService userService,
                           SqlConsoleService sqlConsoleService,
                           UserNotificationService notificationService,
                           AdminMonitoringService adminMonitoringService,
                           MonitoringService monitoringService) {
        this.adminService            = adminService;
        this.communityService        = communityService;
        this.noticeService           = noticeService;
        this.userService             = userService;
        this.sqlConsoleService       = sqlConsoleService;
        this.notificationService     = notificationService;
        this.adminMonitoringService  = adminMonitoringService;
        this.monitoringService       = monitoringService;
    }

    // ===== 공통 모델 속성 (모든 GET 자동 주입) =====

    @ModelAttribute("unconfirmedHighRiskCount")
    public long unconfirmedHighRiskCount(HttpSession session) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) return 0L;
        return userService.findById(uid)
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .map(u -> monitoringService.countUnconfirmedHighRisk())
                .orElse(0L);
    }

    // ===== 대시보드 =====

    @GetMapping({"", "/"})
    public String dashboard(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        model.addAttribute("stats", adminService.stats());
        model.addAttribute("notices", noticeService.findAll());
        return "admin/dashboard";
    }

    // ===== 정서 클러스터 =====

    @GetMapping("/cluster")
    public String clusterViz(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        model.addAttribute("k", k);
        model.addAttribute("weightStress", weightStress);
        model.addAttribute("weightDepression", weightDepression);
        model.addAttribute("weightAnxiety", weightAnxiety);
        model.addAttribute("personaTypes", personaTypes);
        model.addAttribute("variantsPerPersona", variantsPerPersona);
        return "admin/cluster-viz";
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
        model.addAttribute("user", user);
        model.addAttribute("posts", adminService.postsByUser(user));
        model.addAttribute("comments", adminService.commentsByUser(user));
        model.addAttribute("bookings", adminService.bookingsByUser(user));
        model.addAttribute("roles", UserRole.values());
        return "admin/user-detail";
    }

    // ===== 상담 예약 관리 =====

    @GetMapping("/bookings")
    public String bookings(@RequestParam(required = false) String status,
                           HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        Booking.Status filter = parseBookingStatus(status);
        model.addAttribute("bookings", adminService.allBookings(filter));
        model.addAttribute("statuses", Booking.Status.values());
        model.addAttribute("selectedStatus", filter);
        return "admin/bookings";
    }

    @PostMapping("/bookings/{id}/status")
    public String changeBookingStatus(@PathVariable Long id,
                                      @RequestParam String status,
                                      @RequestParam(required = false) String filter,
                                      HttpSession session, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        try {
            adminService.changeBookingStatus(id, Booking.Status.valueOf(status));
            ra.addFlashAttribute("flash", "예약 상태를 변경했습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flash", "상태 변경 실패: " + e.getMessage());
        }
        return (filter != null && !filter.isBlank())
                ? "redirect:/admin/bookings?status=" + filter
                : "redirect:/admin/bookings";
    }

    private Booking.Status parseBookingStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return Booking.Status.valueOf(status);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ===== 알림 발송 (공지와 별도) =====

    @GetMapping("/alerts")
    public String alertsForm(@RequestParam(required = false) Long userId,
                             HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        model.addAttribute("users", userService.findAll());
        model.addAttribute("preselectedUserId", userId);
        return "admin/alerts";
    }

    @PostMapping("/alerts")
    public String sendAlert(@RequestParam(required = false) Long userId,
                            @RequestParam(required = false) String title,
                            @RequestParam String message,
                            @RequestParam(required = false, defaultValue = "false") boolean includeLink,
                            @RequestParam(required = false) String linkUrl,
                            HttpSession session,
                            RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        try {
            int n = notificationService.sendAdminMessage(userId, title, message, includeLink, linkUrl);
            ra.addFlashAttribute("flash", userId != null
                    ? "해당 회원에게 알림을 보냈습니다."
                    : n + "명에게 알림을 보냈습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flash", e.getMessage());
        }
        return "redirect:/admin/alerts";
    }

    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam String role,
                             HttpSession session, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        try {
            userService.changeRole(id, UserRole.valueOf(role));
            ra.addFlashAttribute("flash", "등급이 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flash", "등급 변경 실패: " + e.getMessage());
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
            ra.addFlashAttribute("flash", "삭제 실패: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ===== 게시글 관리 =====

    private static final java.util.List<String> POST_CATEGORIES =
            java.util.List.of("스트레스", "우울", "불안", "인간관계", "일상·기타");

    @GetMapping("/posts")
    public String posts(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        model.addAttribute("posts", communityService.findAll(null));
        return "admin/posts";
    }

    @GetMapping("/posts/new")
    public String postNewForm(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        model.addAttribute("categories", POST_CATEGORIES);
        return "admin/post-new";
    }

    @PostMapping("/posts/new")
    public String postCreate(@RequestParam String title,
                             @RequestParam String content,
                             @RequestParam String category,
                             @RequestParam(value = "files", required = false) org.springframework.web.multipart.MultipartFile[] files,
                             HttpSession session, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        User user = userService.findById((Long) userId).orElseThrow();
        communityService.create(user.getName(), title, content, category, files, null);
        ra.addFlashAttribute("flash", "게시글이 등록되었습니다.");
        return "redirect:/admin/posts";
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
        model.addAttribute("categories", POST_CATEGORIES);
        return "admin/post-edit";
    }

    @PostMapping("/posts/{id}/edit")
    public String postEdit(@PathVariable Long id,
                           @RequestParam String title,
                           @RequestParam String content,
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

    // ===== 공지 관리 =====

    @GetMapping("/notices")
    public String notices(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        model.addAttribute("notices", noticeService.findAll());
        return "admin/notices";
    }

    // ===== SQL 콘솔 =====

    @GetMapping("/sql")
    public String sqlConsole(HttpSession session, RedirectAttributes ra) {
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

    // ===== 모니터링 =====

    @GetMapping("/monitoring")
    public String monitoring(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        model.addAttribute("clusterSummaries",  adminMonitoringService.getClusterSummaries());
        model.addAttribute("highRiskAlerts",    monitoringService.getHighRiskAlertsForAdmin());
        model.addAttribute("unconfirmedCount",  monitoringService.countUnconfirmedHighRisk());
        model.addAttribute("highRiskUsers",     adminMonitoringService.getHighRiskUsers());
        return "admin/monitoring";
    }

    @PostMapping("/monitoring/confirm/{alertId}")
    public String confirmAlert(@PathVariable Long alertId,
                               HttpSession session, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        monitoringService.confirmHighRiskAlert(alertId);
        return "redirect:/admin/monitoring#alerts";
    }

    @PostMapping("/monitoring/message")
    public String sendMonitoringMessage(@RequestParam Long userId,
                                        @RequestParam(required = false) String title,
                                        @RequestParam String message,
                                        HttpSession session, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        try {
            notificationService.sendAdminMessage(userId, title, message, false, null);
            ra.addFlashAttribute("flash", "메시지를 전송했습니다.");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flash", e.getMessage());
        }
        return "redirect:/admin/monitoring#users";
    }

    // ===== 서버 로그 뷰어 =====

    @GetMapping("/logs")
    public String logsView(HttpSession session, RedirectAttributes ra) {
        if (!isAdmin(session)) return denied(ra);
        return "admin/logs";
    }

    // ===== 공통 =====

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
