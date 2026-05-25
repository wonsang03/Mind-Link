package com.mindlink.controller;

import com.mindlink.domain.User;
import com.mindlink.service.MonitoringService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/alerts")
public class AlertController {

    private final MonitoringService monitoringService;
    private final UserService userService;

    public AlertController(MonitoringService monitoringService, UserService userService) {
        this.monitoringService = monitoringService;
        this.userService       = userService;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        User user = getLoginUser(session);
        if (user == null) return "redirect:/login";

        model.addAttribute("alerts", monitoringService.getAllAlerts(user));
        return "alerts";
    }

    @PostMapping("/read-all")
    public String readAll(HttpSession session) {
        User user = getLoginUser(session);
        if (user != null) monitoringService.markAllRead(user);
        return "redirect:/alerts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session) {
        User user = getLoginUser(session);
        if (user != null) monitoringService.deleteAlert(id, user);
        return "redirect:/alerts";
    }

    @PostMapping("/delete-all")
    public String deleteAll(HttpSession session) {
        User user = getLoginUser(session);
        if (user != null) monitoringService.deleteAllAlerts(user);
        return "redirect:/alerts";
    }

    private User getLoginUser(HttpSession session) {
        Object id = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (id instanceof Long uid) return userService.findById(uid).orElse(null);
        return null;
    }
}
