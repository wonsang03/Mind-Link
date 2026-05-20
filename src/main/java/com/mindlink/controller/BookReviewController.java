package com.mindlink.controller;

import com.mindlink.domain.BookReview;
import com.mindlink.service.BookReviewService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reviews")
public class BookReviewController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final BookReviewService service;

    public BookReviewController(BookReviewService service) {
        this.service = service;
    }

    /** GET /api/reviews?bookLink=... */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getReviews(
            @RequestParam String bookLink,
            HttpSession session) {
        Long userId = (Long) session.getAttribute(SessionConst.LOGIN_USER_ID);
        List<Map<String, Object>> list = service.getReviews(bookLink).stream()
                .map(r -> toMap(r, userId))
                .collect(Collectors.toList());
        Map<String, Object> myReview = (userId != null)
                ? service.getMyReview(userId, bookLink).map(r -> toMap(r, userId)).orElse(null)
                : null;
        return ResponseEntity.ok(Map.of(
                "reviews",  list,
                "myReview", myReview != null ? myReview : Map.of()
        ));
    }

    /** POST /api/reviews  body: { bookLink, bookTitle, rating, content } */
    @PostMapping
    public ResponseEntity<Object> save(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        Long userId = (Long) session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (userId == null) return ResponseEntity.status(401).body("로그인이 필요합니다");

        String bookLink  = (String) body.get("bookLink");
        String bookTitle = (String) body.getOrDefault("bookTitle", "");
        int    rating    = ((Number) body.getOrDefault("rating", 3)).intValue();
        String content   = (String) body.getOrDefault("content", "");

        if (bookLink == null || bookLink.isBlank()) {
            return ResponseEntity.badRequest().body("bookLink가 필요합니다");
        }
        BookReview saved = service.save(userId, bookLink, bookTitle, rating, content);
        return ResponseEntity.ok(toMap(saved, userId));
    }

    /** DELETE /api/reviews/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(
            @PathVariable Long id,
            HttpSession session) {
        Long userId = (Long) session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (userId == null) return ResponseEntity.status(401).body("로그인이 필요합니다");
        try {
            service.delete(userId, id);
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }

    private Map<String, Object> toMap(BookReview r, Long currentUserId) {
        return Map.of(
                "id",        r.getId(),
                "userName",  r.getUser().getName(),
                "rating",    r.getRating(),
                "content",   r.getContent() != null ? r.getContent() : "",
                "createdAt", r.getCreatedAt() != null ? r.getCreatedAt().format(FMT) : "",
                "mine",      currentUserId != null && currentUserId.equals(r.getUser().getId())
        );
    }
}
