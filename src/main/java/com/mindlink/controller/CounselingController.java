package com.mindlink.controller;

import com.mindlink.domain.Booking;
import com.mindlink.domain.User;
import com.mindlink.dto.CenterResponse;
import com.mindlink.service.CounselingService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/counseling")
public class CounselingController {

    private final CounselingService counselingService;
    private final UserService userService;

    public CounselingController(CounselingService counselingService, UserService userService) {
        this.counselingService = counselingService;
        this.userService = userService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String query, Model model) {
        List<CenterResponse> centers = counselingService.searchCenters(query);
        String displayQuery = query == null ? "" : query.trim();
        model.addAttribute("centers", centers);
        model.addAttribute("query", displayQuery);
        model.addAttribute("apiConfigured", counselingService.isExternalApiConfigured());
        model.addAttribute("effectiveQuery", counselingService.effectiveSearchQuery(displayQuery));
        if (counselingService.isExternalApiConfigured() && centers.isEmpty()) {
            model.addAttribute("searchHint",
                    "검색 결과가 없습니다. 예: 「목포 심리상담센터」, 「강남 정신건강복지센터」처럼 지역+키워드로 검색해 보세요.");
        }
        return "counseling/list";
    }

    @GetMapping("/booking")
    public String bookingForm(@RequestParam String centerName,
                              @RequestParam(required = false) String centerTitle,
                              @RequestParam(required = false) String centerPhone,
                              @RequestParam(required = false) String centerAddress,
                              HttpSession session,
                              Model model) {
        model.addAttribute("centerName", centerName);
        model.addAttribute("centerTitle", centerTitle == null ? "" : centerTitle);
        model.addAttribute("centerPhone", centerPhone == null ? "" : centerPhone);
        model.addAttribute("centerAddress", centerAddress == null ? "" : centerAddress);
        User user = currentUser(session);
        if (user != null) {
            model.addAttribute("prefillName", user.getName());
            model.addAttribute("prefillEmail", user.getEmail());
        }
        return "counseling/booking";
    }

    @PostMapping("/booking")
    public String submitBooking(@RequestParam String centerName,
                                @RequestParam(required = false) String centerTitle,
                                @RequestParam(required = false) String centerPhone,
                                @RequestParam(required = false) String centerAddress,
                                @RequestParam String name,
                                @RequestParam String phone,
                                @RequestParam String email,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime time,
                                @RequestParam(required = false) String type,
                                @RequestParam(required = false) String memo,
                                HttpSession session,
                                RedirectAttributes ra) {
        User user = currentUser(session);
        try {
            Booking saved = counselingService.createBooking(
                    centerName, centerTitle, centerPhone, centerAddress,
                    user, name, phone, email, date, time, type, memo);
            return "redirect:/counseling/bookings/" + saved.getId();
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flash", e.getMessage());
            return "redirect:/counseling";
        }
    }

    @GetMapping("/bookings/{id}")
    public String bookingDetail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Optional<Booking> booking = counselingService.findBooking(id);
        if (booking.isEmpty()) {
            ra.addFlashAttribute("flash", "예약을 찾을 수 없습니다.");
            return "redirect:/counseling";
        }
        model.addAttribute("booking", booking.get());
        return "counseling/booking-complete";
    }

    private User currentUser(HttpSession session) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (userId instanceof Long id) {
            return userService.findById(id).orElse(null);
        }
        return null;
    }
}
