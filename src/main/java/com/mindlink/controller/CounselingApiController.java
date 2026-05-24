package com.mindlink.controller;

import com.mindlink.domain.Booking;
import com.mindlink.domain.User;
import com.mindlink.dto.BookingRequest;
import com.mindlink.dto.BookingResponse;
import com.mindlink.dto.CenterResponse;
import com.mindlink.service.CounselingService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/counseling")
public class CounselingApiController {

    private final CounselingService counselingService;
    private final UserService userService;

    public CounselingApiController(CounselingService counselingService, UserService userService) {
        this.counselingService = counselingService;
        this.userService = userService;
    }

    @GetMapping("/centers")
    public ResponseEntity<?> searchCenters(@RequestParam(required = false) String query) {
        if (!counselingService.isExternalApiConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "네이버 OpenAPI 키가 설정되지 않았습니다. application.properties의 naver.openapi.client-id/client-secret을 설정하세요."));
        }
        List<CenterResponse> centers = counselingService.searchCenters(query);
        return ResponseEntity.ok(centers);
    }

    @PostMapping("/bookings")
    public ResponseEntity<?> createBooking(@Valid @RequestBody BookingRequest req,
                                           HttpSession session) {
        User user = currentUser(session);
        try {
            Booking saved = counselingService.createBooking(
                    req.getCenterName(), req.getCenterTitle(), req.getCenterPhone(), req.getCenterAddress(),
                    user, req.getName(), req.getPhone(), req.getEmail(),
                    req.getDate(), req.getTime(), req.getType(), req.getMemo());
            BookingResponse body = new BookingResponse(saved);
            return ResponseEntity.created(URI.create("/api/counseling/bookings/" + saved.getId())).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long id) {
        return counselingService.findBooking(id)
                .map(b -> ResponseEntity.ok(new BookingResponse(b)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/bookings/me")
    public ResponseEntity<?> myBookings(HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        List<BookingResponse> body = counselingService.findBookingsByUser(user).stream()
                .map(BookingResponse::new)
                .toList();
        return ResponseEntity.ok(body);
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable Long id, HttpSession session) {
        User user = currentUser(session);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
        }
        try {
            counselingService.cancelBooking(id, user);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    private User currentUser(HttpSession session) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (userId instanceof Long id) {
            Optional<User> user = userService.findById(id);
            return user.orElse(null);
        }
        return null;
    }
}
