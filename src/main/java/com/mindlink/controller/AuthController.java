package com.mindlink.controller;

import com.mindlink.domain.User;
import com.mindlink.dto.LoginRequest;
import com.mindlink.dto.SignupRequest;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
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

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginForm(@ModelAttribute("loginForm") LoginRequest form) {
        return "login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginForm") LoginRequest form,
                        BindingResult bindingResult,
                        HttpSession session) {
        if (bindingResult.hasErrors()) return "login";

        Optional<User> user = userService.login(form.getEmail(), form.getPassword());
        if (user.isEmpty()) {
            bindingResult.reject("loginFail", "이메일 또는 비밀번호가 올바르지 않습니다.");
            return "login";
        }

        session.setAttribute(SessionConst.LOGIN_USER_ID, user.get().getId());
        return "redirect:/";
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
            User user = userService.signup(form.getName(), form.getEmail(), form.getPassword());
            session.setAttribute(SessionConst.LOGIN_USER_ID, user.getId());
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("email", "duplicate", e.getMessage());
            return "signup";
        }
        return "redirect:/";
    }
}
