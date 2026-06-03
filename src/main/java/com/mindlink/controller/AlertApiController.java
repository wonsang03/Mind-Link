package com.mindlink.controller;

import com.mindlink.domain.User;
import com.mindlink.service.MonitoringService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 알림 폴링용 REST — 헤더 종 배지 주기 갱신에 사용. */
@RestController
@RequestMapping("/api/alerts")
public class AlertApiController {

    private final MonitoringService monitoringService;
    private final UserService userService;

    public AlertApiController(MonitoringService monitoringService, UserService userService) {
        this.monitoringService = monitoringService;
        this.userService = userService;
    }

    /** 읽지 않은 알림 수. 비로그인/미존재 사용자는 0. */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(HttpSession session) {
        long count = 0;
        Object id = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (id instanceof Long uid) {
            User user = userService.findById(uid).orElse(null);
            if (user != null) count = monitoringService.countUnread(user);
        }
        return ResponseEntity.ok(Map.of("count", count));
    }
}
