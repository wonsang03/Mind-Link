package com.mindlink.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindlink.domain.ActivityLog;
import com.mindlink.service.ActivityService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 추천 활동 전용 실행 페이지(/activities/{key})와 활동 일지(/activities/history).
 * 활동 수행 자체는 비로그인도 가능하나, 일지 조회는 로그인 필요.
 */
@Controller
@RequestMapping("/activities")
public class ActivityController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final ActivityService activityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /** 활동 실행 페이지 */
    @GetMapping("/{key}")
    public String run(@PathVariable String key, Model model, RedirectAttributes ra) {
        if (!activityService.isValidKey(key)) {
            ra.addFlashAttribute("flash", "알 수 없는 활동입니다.");
            return "redirect:/recommendations";
        }
        model.addAttribute("activityKey", key);
        model.addAttribute("activityLabel", activityService.labelOf(key));
        model.addAttribute("activityMeta", activityService.metaOf(key));
        return "activity/run";
    }

    /** 활동 일지 — 내가 수행한 활동 기록 */
    @GetMapping("/history")
    public String history(HttpSession session, Model model, RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }

        List<ActivityLog> logs = activityService.recent(uid);
        List<Map<String, Object>> items = new ArrayList<>();
        for (ActivityLog log : logs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", activityService.labelOf(log.getActivityKey()));
            m.put("time", log.getCreatedAt() != null ? log.getCreatedAt().format(FMT) : "");
            m.put("detail", detailOf(log));
            items.add(m);
        }

        model.addAttribute("items", items);
        model.addAttribute("total", activityService.countTotal(uid));
        model.addAttribute("week", activityService.countThisWeek(uid));
        return "activity/history";
    }

    /** payload(JSON) 를 사람이 읽을 수 있는 한 줄로 변환 */
    @SuppressWarnings("unchecked")
    private String detailOf(ActivityLog log) {
        String payload = log.getPayload();
        if (payload == null || payload.isBlank()) return "";
        try {
            Map<String, Object> data = objectMapper.readValue(payload, new TypeReference<>() {});
            if (data.get("items") instanceof List<?> list
                    && Set.of("gratitude", "grounding", "thought_dump", "small_action")
                            .contains(log.getActivityKey())) {
                return String.join(" · ", list.stream().map(String::valueOf).toList());
            }
            if ("checkin".equals(log.getActivityKey())) {
                Object emoji = data.getOrDefault("emoji", "");
                Object mood = data.getOrDefault("mood", "");
                return (emoji + " " + mood).trim();
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }
}
