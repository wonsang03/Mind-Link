package com.mindlink.care;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mindlink.domain.AssessmentChoice;
import com.mindlink.domain.AssessmentQuestion;
import com.mindlink.domain.AssessmentType;
import com.mindlink.domain.User;
import com.mindlink.service.AssessmentService;
import com.mindlink.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI 종합 보고서 위저드 + 결과/목록 페이지.
 *  /care-report           → /care-report/wizard 리다이렉트
 *  /care-report/wizard    10단계 위저드
 *  /care-report/list      내 보고서 목록
 *  /care-report/{id}      편지 읽기
 * 비로그인은 모두 /login 으로.
 */
@Controller
@RequestMapping("/care-report")
public class CareReportPageController {

    private static final List<String> WIZARD_ASSESSMENTS = List.of("stress", "depression", "anxiety");

    private final CareReportService service;
    private final UserService userService;
    private final AssessmentService assessmentService;
    private final ObjectMapper mapper = new ObjectMapper();

    public CareReportPageController(CareReportService service,
                                     UserService userService,
                                     AssessmentService assessmentService) {
        this.service = service;
        this.userService = userService;
        this.assessmentService = assessmentService;
    }

    @GetMapping
    public String index() {
        return "redirect:/care-report/wizard";
    }

    @GetMapping("/wizard")
    public String wizard(HttpSession session, Model model) {
        User user = CareWebSupport.currentUser(session, userService);
        if (user == null) return "redirect:/login";

        ArrayNode arr = mapper.createArrayNode();
        for (String typeKey : WIZARD_ASSESSMENTS) {
            Optional<AssessmentType> opt = assessmentService.findByTypeKey(typeKey);
            if (opt.isEmpty()) continue;
            AssessmentType t = opt.get();
            ObjectNode node = arr.addObject();
            node.put("typeKey", t.getTypeKey());
            node.put("name", t.getName());
            node.put("description", t.getDescription());

            ArrayNode questionsArr = node.putArray("questions");
            for (AssessmentQuestion q : t.getQuestions()) {
                ObjectNode qn = questionsArr.addObject();
                qn.put("order", q.getOrderNum());
                qn.put("content", q.getContent());
            }

            ArrayNode choicesArr = node.putArray("choices");
            for (AssessmentChoice c : t.getChoices()) {
                ObjectNode cn = choicesArr.addObject();
                cn.put("order", c.getOrderNum());
                cn.put("label", c.getLabel());
                cn.put("score", c.getScore());
            }
        }
        String json;
        try {
            json = mapper.writeValueAsString(arr);
        } catch (Exception e) {
            json = "[]";
        }

        model.addAttribute("userName", user.getName());
        model.addAttribute("assessmentsJson", json);
        return "care-report/wizard";
    }

    @GetMapping("/list")
    public String list(HttpSession session, Model model) {
        User user = CareWebSupport.currentUser(session, userService);
        if (user == null) return "redirect:/login";
        List<CareReport> reports = service.myList(user.getId());
        List<Map<String, Object>> items = new ArrayList<>();
        for (CareReport r : reports) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("createdAt", r.getCreatedAt());
            m.put("riskLevel", r.getRiskLevel() != null ? r.getRiskLevel().name() : "NORMAL");
            m.put("excerpt", CareWebSupport.excerpt(r.getLetterBody(), 140));
            m.put("themes", r.getThemes() == null ? "" : r.getThemes());
            items.add(m);
        }
        model.addAttribute("items", items);
        return "care-report/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, HttpSession session, Model model) {
        User user = CareWebSupport.currentUser(session, userService);
        if (user == null) return "redirect:/login";
        Optional<CareReport> opt = service.findMine(user.getId(), id);
        if (opt.isEmpty()) return "redirect:/care-report/list";
        CareReport r = opt.get();
        model.addAttribute("report", r);
        model.addAttribute("paragraphs", splitParagraphs(r.getLetterBody()));
        model.addAttribute("themes", CareReportService.splitThemes(r.getThemes()));
        return "care-report/detail";
    }

    private static List<String> splitParagraphs(String body) {
        if (body == null || body.isBlank()) return List.of();
        String[] parts = body.replace("\r", "").split("\n\\s*\n");
        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
