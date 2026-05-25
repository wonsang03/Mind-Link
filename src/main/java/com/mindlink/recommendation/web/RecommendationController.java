package com.mindlink.recommendation.web;

import com.mindlink.recommendation.dto.AiRecommendationRequest;
import com.mindlink.recommendation.dto.BookDto;
import com.mindlink.recommendation.dto.PersonalizeRequest;
import com.mindlink.recommendation.dto.RecommendationResponse;
import com.mindlink.recommendation.service.RecommendationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private static final String SESSION_AI_ISBN_HISTORY = "ml_ai_recent_isbns";

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    /**
     * GET /api/recommendations?emotion=DEPRESSION&size=5
     *
     * @param emotion 감정 상태 (DEPRESSION / STRESS / ANXIETY / LETHARGY / RELATIONSHIP / NORMAL)
     * @param size    반환 도서 수 (최대 20)
     *
     * 향후: Spring Security + JWT 연동 시 이 경로에 인증 요구 추가
     */
    @GetMapping
    RecommendationResponse getRecommendations(
            @RequestParam(defaultValue = "NORMAL") String emotion,
            @RequestParam(defaultValue = "5")      int size) {
        return service.getRecommendations(emotion, Math.min(Math.max(size, 1), 20));
    }

    /** POST /api/recommendations/personalize — DB only, 네이버 API 호출 없음 */
    @PostMapping("/personalize")
    RecommendationResponse getPersonalizedRecommendations(@RequestBody PersonalizeRequest req, HttpSession session) {
        String msg = req.getMessage();
        if (msg == null || msg.isBlank())
            return new RecommendationResponse("NORMAL", "메시지를 입력해 주세요.", List.of(), "EMPTY", true);
        String safe = msg.trim();
        if (safe.length() > 500) safe = safe.substring(0, 500);

        @SuppressWarnings("unchecked")
        List<String> hist = (List<String>) session.getAttribute(SESSION_AI_ISBN_HISTORY);
        List<String> exclude = new ArrayList<>();
        if (hist != null) {
            int from = Math.max(0, hist.size() - 35);
            exclude.addAll(hist.subList(from, hist.size()));
        }

        RecommendationResponse resp = service.getPersonalizedRecommendations(safe, req.getEmotion(), exclude);
        updateIsbnHistory(session, resp, exclude);
        return resp;
    }

    /** POST /api/recommendations/ai — DB 기반 내부 전환 (네이버 호출 없음) */
    @PostMapping("/ai")
    RecommendationResponse getAiRecommendations(@RequestBody AiRecommendationRequest req, HttpSession session) {
        String msg = req.getMessage();
        if (msg == null || msg.isBlank())
            return new RecommendationResponse("NORMAL", "메시지를 입력해 주세요.", List.of(), "EMPTY", true);
        String safe = msg.trim();
        if (safe.length() > 500) safe = safe.substring(0, 500);

        @SuppressWarnings("unchecked")
        List<String> hist = (List<String>) session.getAttribute(SESSION_AI_ISBN_HISTORY);
        List<String> exclude = new ArrayList<>();
        if (hist != null) {
            int from = Math.max(0, hist.size() - 35);
            exclude.addAll(hist.subList(from, hist.size()));
        }

        RecommendationResponse resp = service.getPersonalizedRecommendations(safe, null, exclude);
        updateIsbnHistory(session, resp, exclude);
        return resp;
    }

    private void updateIsbnHistory(HttpSession session, RecommendationResponse resp, List<String> exclude) {
        if (resp.getBooks() != null && !resp.getBooks().isEmpty()) {
            List<String> next = new ArrayList<>(exclude);
            for (BookDto b : resp.getBooks()) {
                if (b.getIsbn() != null && !b.getIsbn().isBlank()) next.add(b.getIsbn());
            }
            final int maxHist = 40;
            while (next.size() > maxHist) next.remove(0);
            session.setAttribute(SESSION_AI_ISBN_HISTORY, next);
        }
    }
}
