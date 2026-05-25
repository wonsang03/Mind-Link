package com.mindlink.controller;

import com.mindlink.domain.Attachment;
import com.mindlink.domain.Post;
import com.mindlink.domain.PostComment;
import com.mindlink.domain.Report;
import com.mindlink.domain.User;
import com.mindlink.dto.AttachmentResponse;
import com.mindlink.service.CommunityCategoryPreferenceService;
import com.mindlink.service.CommunityService;
import com.mindlink.service.FileStorageService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/community")
public class CommunityController {

    /** 고민 공유 소주제 카테고리 */
    public static final List<String> CATEGORIES =
            List.of("전체", "스트레스", "우울", "불안", "인간관계", "일상·기타");

    private final CommunityService communityService;
    private final UserService userService;
    private final FileStorageService fileStorageService;
    private final CommunityCategoryPreferenceService categoryPreferenceService;

    public CommunityController(CommunityService communityService,
                                UserService userService,
                                FileStorageService fileStorageService,
                                CommunityCategoryPreferenceService categoryPreferenceService) {
        this.communityService = communityService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.categoryPreferenceService = categoryPreferenceService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       HttpSession session,
                       Model model) {
        // 로그인 유저 맞춤 카테고리 추론
        Object userIdAttr = session.getAttribute(SessionConst.LOGIN_USER_ID);
        Long uid = (userIdAttr instanceof Long l) ? l : null;
        List<String> preferredCategories = categoryPreferenceService.resolvePreferredCategories(uid);
        String defaultCategory = preferredCategories.isEmpty() ? "전체" : preferredCategories.get(0);

        String selectedCategory = (category != null && !category.isBlank()) ? category : defaultCategory;

        List<Post> posts = communityService.findAll(selectedCategory);
        if (q != null && !q.isBlank()) {
            String query = q.toLowerCase();
            posts = posts.stream()
                    .filter(p -> p.getTitle().toLowerCase().contains(query)
                            || p.getContent().toLowerCase().contains(query))
                    .toList();
        }

        // 맞춤 카테고리가 있고 전체 보기일 때: 해당 카테고리 글을 상단에 정렬
        if (selectedCategory.equals("전체") && !preferredCategories.isEmpty()) {
            String preferred = preferredCategories.get(0);
            List<Post> sorted = new ArrayList<>();
            posts.stream().filter(p -> preferred.equals(p.getCategory())).forEach(sorted::add);
            posts.stream().filter(p -> !preferred.equals(p.getCategory())).forEach(sorted::add);
            posts = sorted;
        }

        model.addAttribute("posts", posts);
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("selectedCategory", selectedCategory);
        model.addAttribute("query", q);
        model.addAttribute("preferredCategories", preferredCategories);
        model.addAttribute("highlightCategory", preferredCategories.isEmpty() ? null : preferredCategories.get(0));
        return "community/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Optional<Post> post = communityService.findById(id);
        if (post.isEmpty()) return "redirect:/community";

        Post p = post.get();
        model.addAttribute("post", p);
        model.addAttribute("postAttachments",
                fileStorageService.findByTarget(Attachment.TargetType.POST, p.getId()));

        Map<Long, List<AttachmentResponse>> commentAttachments = new HashMap<>();
        for (PostComment c : p.getComments()) {
            List<AttachmentResponse> list =
                    fileStorageService.findByTarget(Attachment.TargetType.COMMENT, c.getId());
            if (!list.isEmpty()) {
                commentAttachments.put(c.getId(), list);
            }
        }
        model.addAttribute("commentAttachments", commentAttachments);
        return "community/detail";
    }

    @GetMapping("/new")
    public String newForm(HttpSession session, Model model, RedirectAttributes ra) {
        if (session.getAttribute(SessionConst.LOGIN_USER_ID) == null) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        model.addAttribute("categories",
                CATEGORIES.stream().filter(c -> !"전체".equals(c)).toList());
        return "community/new";
    }

    @PostMapping
    public String create(@RequestParam String title,
                         @RequestParam String content,
                         @RequestParam String category,
                         @RequestParam(value = "files", required = false) MultipartFile[] files,
                         @RequestParam(value = "linkUrls", required = false) String[] linkUrls,
                         HttpSession session,
                         RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long id)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        User user = userService.findById(id).orElseThrow();
        try {
            Post saved = communityService.create(user.getName(), title, content, category, files, linkUrls);
            return "redirect:/community/" + saved.getId();
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flash", e.getMessage());
            return "redirect:/community/new";
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, HttpSession session,
                           Model model, RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        Post post = communityService.findById(id).orElse(null);
        if (post == null) return "redirect:/community";

        User user = userService.findById(uid).orElseThrow();
        if (!post.getAuthor().equals(user.getName())) {
            ra.addFlashAttribute("flash", "본인이 작성한 게시글만 수정할 수 있습니다.");
            return "redirect:/community/" + id;
        }
        model.addAttribute("post", post);
        model.addAttribute("postAttachments",
                fileStorageService.findByTarget(Attachment.TargetType.POST, id));
        model.addAttribute("categories",
                CATEGORIES.stream().filter(c -> !"전체".equals(c)).toList());
        return "community/edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @RequestParam String title,
                       @RequestParam String content,
                       @RequestParam String category,
                       @RequestParam(value = "removeAttachmentIds", required = false) List<Long> removeAttachmentIds,
                       @RequestParam(value = "files", required = false) MultipartFile[] files,
                       @RequestParam(value = "linkUrls", required = false) String[] linkUrls,
                       HttpSession session,
                       RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        User user = userService.findById(uid).orElseThrow();
        try {
            communityService.updatePost(id, user.getName(), title, content, category,
                    removeAttachmentIds, files, linkUrls);
            ra.addFlashAttribute("flash", "게시글이 수정되었습니다.");
            return "redirect:/community/" + id;
        } catch (SecurityException e) {
            ra.addFlashAttribute("flash", e.getMessage());
            return "redirect:/community/" + id;
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flash", e.getMessage());
            return "redirect:/community/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/comments")
    public String addComment(@PathVariable Long id,
                             @RequestParam String content,
                             @RequestParam(value = "parentCommentId", required = false) Long parentCommentId,
                             @RequestParam(value = "files", required = false) MultipartFile[] files,
                             @RequestParam(value = "linkUrls", required = false) String[] linkUrls,
                             HttpSession session,
                             RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        User user = userService.findById(uid).orElseThrow();
        try {
            var saved = communityService.addComment(id, user.getName(), content, parentCommentId, files, linkUrls);
            return "redirect:/community/" + id + "#comment-" + saved.getId();
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("flash", e.getMessage());
        }
        return "redirect:/community/" + id;
    }

    @PostMapping("/{id}/like")
    public String like(@PathVariable Long id) {
        communityService.like(id);
        return "redirect:/community/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deletePost(@PathVariable Long id,
                             HttpSession session,
                             RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        User user = userService.findById(uid).orElseThrow();
        try {
            communityService.deletePost(id, user.getName());
            ra.addFlashAttribute("flash", "게시글이 삭제되었습니다.");
        } catch (SecurityException e) {
            ra.addFlashAttribute("flash", e.getMessage());
            return "redirect:/community/" + id;
        }
        return "redirect:/community";
    }

    @PostMapping("/{postId}/comments/{commentId}/delete")
    public String deleteComment(@PathVariable Long postId,
                                @PathVariable Long commentId,
                                HttpSession session,
                                RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        User user = userService.findById(uid).orElseThrow();
        try {
            communityService.deleteComment(commentId, user.getName());
        } catch (SecurityException e) {
            ra.addFlashAttribute("flash", e.getMessage());
        }
        return "redirect:/community/" + postId;
    }

    @PostMapping("/{id}/report")
    public String reportPost(@PathVariable Long id,
                             @RequestParam String reason,
                             HttpSession session,
                             RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        User user = userService.findById(uid).orElseThrow();
        communityService.report(user, Report.TargetType.POST, id, reason);
        ra.addFlashAttribute("flash", "신고가 접수되었습니다.");
        return "redirect:/community/" + id;
    }

    @PostMapping("/{postId}/comments/{commentId}/report")
    public String reportComment(@PathVariable Long postId,
                                @PathVariable Long commentId,
                                @RequestParam String reason,
                                HttpSession session,
                                RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        User user = userService.findById(uid).orElseThrow();
        communityService.report(user, Report.TargetType.COMMENT, commentId, reason);
        ra.addFlashAttribute("flash", "신고가 접수되었습니다.");
        return "redirect:/community/" + postId;
    }
}
