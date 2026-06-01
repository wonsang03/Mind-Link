package com.mindlink.controller;

import com.mindlink.domain.User;
import com.mindlink.dto.UserResponse;
import com.mindlink.dto.UserUpdateRequest;
import com.mindlink.service.FileStorageService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final FileStorageService fileStorageService;

    public UserController(UserService userService, FileStorageService fileStorageService) {
        this.userService = userService;
        this.fileStorageService = fileStorageService;
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
        form.setNickname(user.getNickname());
        form.setRegion(user.getRegion());
        form.setNotificationEnabled(user.getNotificationEnabled());
        form.setPhone(user.getPhone());
        model.addAttribute("updateForm", form);
        model.addAttribute("user", new UserResponse(user));
        return "user/edit";
    }

    /** 민감정보 동의 토글 */
    @PostMapping("/me/sensitive-consent")
    public String updateSensitiveConsent(@RequestParam boolean consent,
                                         HttpSession session,
                                         RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) return "redirect:/login";
        userService.updateSensitiveConsent(uid, consent);
        ra.addFlashAttribute("flash",
                consent ? "자가진단 결과 저장에 동의하였습니다."
                        : "동의가 철회되었습니다. 저장된 자가진단 결과와 알림이 삭제되었습니다.");
        return "redirect:/user/me";
    }

    /** 회원 탈퇴 처리 */
    @PostMapping("/me/withdraw")
    public String withdraw(@RequestParam String currentPassword,
                           HttpSession session,
                           RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) return "redirect:/login";

        if (!userService.checkPassword(uid, currentPassword)) {
            ra.addFlashAttribute("withdrawError", "비밀번호가 올바르지 않습니다.");
            return "redirect:/user/me";
        }

        userService.withdraw(uid);
        session.invalidate();
        ra.addFlashAttribute("flash", "회원 탈퇴가 완료되었습니다. 이용해 주셔서 감사합니다.");
        return "redirect:/";
    }

    /** 회원 정보 수정 처리 */
    @PostMapping("/me/edit")
    public String edit(@Valid @ModelAttribute("updateForm") UserUpdateRequest form,
                       BindingResult bindingResult,
                       @RequestParam(value = "profileImage", required = false) MultipartFile profileImage,
                       HttpSession session,
                       Model model,
                       RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            User user = userService.findById(uid)
                    .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));
            model.addAttribute("user", new UserResponse(user));
            return "user/edit";
        }

        // 프로필 사진 저장 처리
        String newImageUrl = null;
        if (profileImage != null && !profileImage.isEmpty()) {
            try {
                newImageUrl = fileStorageService.saveProfileImage(profileImage);
            } catch (IllegalArgumentException e) {
                model.addAttribute("imageError", e.getMessage());
                User user = userService.findById(uid)
                        .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));
                model.addAttribute("user", new UserResponse(user));
                return "user/edit";
            }
        }

        // 기존 프로필 사진 URL 저장 (교체 시 삭제용)
        String oldImageUrl = (newImageUrl != null)
                ? userService.findById(uid).map(User::getProfileImageUrl).orElse(null)
                : null;

        try {
            userService.updateProfile(uid, form, newImageUrl);
        } catch (IllegalArgumentException e) {
            if (newImageUrl != null) fileStorageService.deleteProfileImage(newImageUrl);
            bindingResult.rejectValue("email", "duplicate", e.getMessage());
            User user = userService.findById(uid)
                    .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));
            model.addAttribute("user", new UserResponse(user));
            return "user/edit";
        }

        // 교체된 기존 사진 파일 삭제
        if (newImageUrl != null && oldImageUrl != null) {
            fileStorageService.deleteProfileImage(oldImageUrl);
        }

        ra.addFlashAttribute("flash", "회원 정보가 수정되었습니다.");
        return "redirect:/user/me";
    }
}
