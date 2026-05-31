package com.mindlink.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindlink.service.ActivityService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/activities")
public class ActivityApiController {

    private final ActivityService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ActivityApiController(ActivityService service) {
        this.service = service;
    }

    /**
     * POST /api/activities
     * body: { activityKey, payload?: object, moodScore?: number, durationSec?: number, programKey?: string }
     * 활동 1회 완료를 기록한다. 비로그인은 조용히 무시(204)하여 활동 UX 를 막지 않는다.
     */
    @PostMapping
    public ResponseEntity<Object> log(@RequestBody Map<String, Object> body, HttpSession session) {
        Long userId = (Long) session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (userId == null) return ResponseEntity.noContent().build();

        String activityKey = (String) body.get("activityKey");
        if (!service.isValidKey(activityKey)) {
            return ResponseEntity.badRequest().body("알 수 없는 활동입니다");
        }

        String payloadJson = null;
        Object payload = body.get("payload");
        if (payload != null) {
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (Exception ignored) {
                payloadJson = null;
            }
        }

        Integer moodScore   = toInt(body.get("moodScore"));
        Integer durationSec = toInt(body.get("durationSec"));
        String  programKey  = (String) body.get("programKey");

        service.log(userId, activityKey, payloadJson, moodScore, durationSec, programKey);
        return ResponseEntity.ok().build();
    }

    private Integer toInt(Object o) {
        return (o instanceof Number n) ? n.intValue() : null;
    }
}
