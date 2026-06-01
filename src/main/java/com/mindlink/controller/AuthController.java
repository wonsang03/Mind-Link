package com.mindlink.controller;

import com.mindlink.domain.User;
import com.mindlink.dto.LoginRequest;
import com.mindlink.dto.SignupRequest;
import com.mindlink.service.LoginAttemptService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Optional;

@Controller
public class AuthController {

    private final UserService userService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(UserService userService, LoginAttemptService loginAttemptService) {
        this.userService = userService;
        this.loginAttemptService = loginAttemptService;
    }

    @GetMapping("/login")
    public String loginForm(@ModelAttribute("loginForm") LoginRequest form) {
        return "login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginForm") LoginRequest form,
                        BindingResult bindingResult,
                        HttpSession session,
                        HttpServletRequest request) {
        if (bindingResult.hasErrors()) return "login";

        String ip = extractIp(request);

        // IP 속도 제한 체크
        if (loginAttemptService.isIpBlocked(ip)) {
            bindingResult.reject("loginFail", "너무 많은 로그인 시도가 감지되었습니다. 잠시 후 다시 시도해주세요.");
            return "login";
        }

        // 계정 잠금 체크
        if (loginAttemptService.isAccountLocked(form.getEmail())) {
            long minutes = loginAttemptService.getAccountLockRemaining(form.getEmail()).toMinutes() + 1;
            bindingResult.reject("loginFail", "계정이 잠겼습니다. 약 " + minutes + "분 후 다시 시도해주세요.");
            return "login";
        }

        loginAttemptService.recordIpAttempt(ip);

        Optional<User> user = userService.login(form.getEmail(), form.getPassword());
        if (user.isEmpty()) {
            loginAttemptService.recordLoginFailure(form.getEmail());
            bindingResult.reject("loginFail", "이메일 또는 비밀번호가 올바르지 않습니다.");
            return "login";
        }

        loginAttemptService.recordLoginSuccess(form.getEmail());
        session.invalidate();                                                   // 기존 세션 파기 (세션 고정 방지)
        HttpSession newSession = request.getSession(true);                      // 새 세션 발급
        newSession.setAttribute(SessionConst.LOGIN_USER_ID, user.get().getId());
        return "redirect:/";
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/signup")
    public String signupForm(@ModelAttribute("signupForm") SignupRequest form) {
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("signupForm") SignupRequest form,
                         BindingResult bindingResult,
                         HttpSession session) {
        if (!form.passwordMatches()) {
            bindingResult.rejectValue("passwordConfirm", "mismatch", "비밀번호가 일치하지 않습니다.");
        }
        if (bindingResult.hasErrors()) return "signup";

        try {
            User user = userService.signup(form.getName(), form.getEmail(), form.getPassword(), form.isAgreeSensitive());
            session.setAttribute(SessionConst.LOGIN_USER_ID, user.getId());
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("email", "duplicate", e.getMessage());
            return "signup";
        }
        return "redirect:/";
    }
}
