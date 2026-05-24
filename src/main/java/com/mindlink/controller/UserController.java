package com.mindlink.controller;

import com.mindlink.domain.User;
import com.mindlink.dto.UserResponse;
import com.mindlink.dto.UserUpdateRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 내 계정 정보 조회 */
    @GetMapping("/me")
    public String me(HttpSession session, Model model, RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        User user = userService.findById(uid)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));
        model.addAttribute("user", new UserResponse(user));
        return "user/me";
    }

    /** 회원 정보 수정 폼 */
    @GetMapping("/me/edit")
    public String editForm(HttpSession session, Model model, RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        User user = userService.findById(uid)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));

        UserUpdateRequest form = new UserUpdateRequest();
        form.setName(user.getName());
        form.setEmail(user.getEmail());
        model.addAttribute("updateForm", form);
        model.addAttribute("user", new UserResponse(user));
        return "user/edit";
    }

    /** 회원 정보 수정 처리 (이름·이메일 + 선택적 비밀번호 변경) */
    @PostMapping("/me/edit")
    public String edit(@Valid @ModelAttribute("updateForm") UserUpdateRequest form,
                       BindingResult bindingResult,
                       @RequestParam(required = false) String currentPassword,
                       @RequestParam(required = false) String newPassword,
                       @RequestParam(required = false) String newPasswordConfirm,
                       HttpSession session,
                       Model model,
                       RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", currentUserResponse(uid));
            return "user/edit";
        }

        try {
            userService.updateProfile(uid, form.getName(), form.getEmail());
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("email", "duplicate", e.getMessage());
            model.addAttribute("user", currentUserResponse(uid));
            return "user/edit";
        }

        // 비밀번호는 입력했을 때만 변경
        if (newPassword != null && !newPassword.isBlank()) {
            if (newPassword.length() < 4) {
                ra.addFlashAttribute("flash", "정보는 저장되었으나, 새 비밀번호는 4자 이상이어야 합니다.");
                return "redirect:/user/me/edit";
            }
            if (!newPassword.equals(newPasswordConfirm)) {
                ra.addFlashAttribute("flash", "정보는 저장되었으나, 새 비밀번호가 일치하지 않습니다.");
                return "redirect:/user/me/edit";
            }
            try {
                userService.changePassword(uid, currentPassword == null ? "" : currentPassword, newPassword);
            } catch (IllegalArgumentException e) {
                ra.addFlashAttribute("flash", "정보는 저장되었으나, " + e.getMessage());
                return "redirect:/user/me/edit";
            }
            ra.addFlashAttribute("flash", "회원 정보와 비밀번호가 수정되었습니다.");
            return "redirect:/user/me";
        }

        ra.addFlashAttribute("flash", "회원 정보가 수정되었습니다.");
        return "redirect:/user/me";
    }

    private UserResponse currentUserResponse(Long uid) {
        User user = userService.findById(uid)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));
        return new UserResponse(user);
    }
}
