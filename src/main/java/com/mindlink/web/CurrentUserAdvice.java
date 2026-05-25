package com.mindlink.web;

import com.mindlink.domain.User;
import com.mindlink.service.MonitoringService;
import com.mindlink.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrentUserAdvice {

    private final UserService userService;
    private final MonitoringService monitoringService;

    public CurrentUserAdvice(UserService userService, MonitoringService monitoringService) {
        this.userService = userService;
        this.monitoringService = monitoringService;
    }

    @ModelAttribute("loginUser")
    public User loginUser(HttpSession session) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (userId instanceof Long id) {
            return userService.findById(id).orElse(null);
        }
        return null;
    }

    @ModelAttribute("unreadAlertCount")
    public long unreadAlertCount(HttpSession session) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (userId instanceof Long id) {
            return userService.findById(id)
                    .map(monitoringService::countUnread)
                    .orElse(0L);
        }
        return 0L;
    }

    @ModelAttribute("currentUri")
    public String currentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
