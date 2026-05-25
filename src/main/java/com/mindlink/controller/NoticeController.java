package com.mindlink.controller;

import com.mindlink.domain.Notice;
import com.mindlink.domain.UserRole;
import com.mindlink.dto.NoticeRequest;
import com.mindlink.service.NoticeService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/notice")
public class NoticeController {

    private final NoticeService noticeService;
    private final UserService userService;

    public NoticeController(NoticeService noticeService, UserService userService) {
        this.noticeService = noticeService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("notices", noticeService.findAll());
        return "notice";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Notice notice = noticeService.findById(id).orElse(null);
        if (notice == null) {
            ra.addFlashAttribute("flash", "존재하지 않는 공지입니다.");
            return "redirect:/notice";
        }
        model.addAttribute("notice", notice);
        return "notice-detail";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) {
            ra.addFlashAttribute("flash", "관리자만 공지를 작성할 수 있습니다.");
            return "redirect:/notice";
        }
        model.addAttribute("noticeForm", new NoticeRequest());
        model.addAttribute("isEdit", false);
        return "admin/notice-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("noticeForm") NoticeRequest form,
                         BindingResult bindingResult,
                         @RequestParam(value = "sendPushAlert", defaultValue = "false") boolean sendPushAlert,
                         HttpSession session,
                         Model model,
                         RedirectAttributes ra) {
        if (!isAdmin(session)) {
            ra.addFlashAttribute("flash", "관리자만 공지를 작성할 수 있습니다.");
            return "redirect:/notice";
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "admin/notice-form";
        }
        noticeService.create(form, sendPushAlert);
        String msg = "공지가 등록되었습니다.";
        if (sendPushAlert) {
            msg += " 회원 알림함에도 발송했습니다. (공지 게시판과 별도)";
        }
        ra.addFlashAttribute("flash", msg);
        return "redirect:/admin/notices";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session,
                           Model model, RedirectAttributes ra) {
        if (!isAdmin(session)) {
            ra.addFlashAttribute("flash", "관리자만 공지를 수정할 수 있습니다.");
            return "redirect:/notice/" + id;
        }
        Notice notice = noticeService.findById(id).orElse(null);
        if (notice == null) {
            ra.addFlashAttribute("flash", "존재하지 않는 공지입니다.");
            return "redirect:/notice";
        }
        NoticeRequest form = new NoticeRequest();
        form.setCategory(notice.getCategory());
        form.setTitle(notice.getTitle());
        form.setSummary(notice.getSummary());
        form.setContent(notice.getContent());
        model.addAttribute("noticeForm", form);
        model.addAttribute("noticeId", id);
        model.addAttribute("isEdit", true);
        return "admin/notice-form";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @Valid @ModelAttribute("noticeForm") NoticeRequest form,
                       BindingResult bindingResult,
                       HttpSession session,
                       Model model,
                       RedirectAttributes ra) {
        if (!isAdmin(session)) {
            ra.addFlashAttribute("flash", "관리자만 공지를 수정할 수 있습니다.");
            return "redirect:/notice/" + id;
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("noticeId", id);
            model.addAttribute("isEdit", true);
            return "admin/notice-form";
        }
        noticeService.update(id, form);
        ra.addFlashAttribute("flash", "공지가 수정되었습니다.");
        return "redirect:/admin/notices";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        if (!isAdmin(session)) {
            ra.addFlashAttribute("flash", "관리자만 공지를 삭제할 수 있습니다.");
            return "redirect:/notice/" + id;
        }
        noticeService.delete(id);
        ra.addFlashAttribute("flash", "공지가 삭제되었습니다.");
        return "redirect:/admin/notices";
    }

    private boolean isAdmin(HttpSession session) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) return false;
        return userService.findById(uid)
                .map(u -> u.getRole() == UserRole.ADMIN)
                .orElse(false);
    }
}
