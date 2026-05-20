package com.mindlink.controller;

import com.mindlink.domain.Post;
import com.mindlink.domain.User;
import com.mindlink.service.PostService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/community")
public class CommunityController {

    public static final List<String> CATEGORIES =
            List.of("전체", "스트레스 관리", "경험 공유", "함께 해요", "질문과 답변", "추천 및 후기");

    private final PostService postService;
    private final UserService userService;

    public CommunityController(PostService postService, UserService userService) {
        this.postService = postService;
        this.userService = userService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "전체") String category,
                       @RequestParam(required = false) String q,
                       Model model) {
        List<Post> posts = postService.findAll(category);
        if (q != null && !q.isBlank()) {
            String query = q.toLowerCase();
            posts = posts.stream()
                    .filter(p -> p.getTitle().toLowerCase().contains(query)
                            || p.getContent().toLowerCase().contains(query))
                    .toList();
        }
        model.addAttribute("posts", posts);
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("query", q);
        return "community/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Optional<Post> post = postService.findById(id);
        if (post.isEmpty()) return "redirect:/community";
        model.addAttribute("post", post.get());
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
                         HttpSession session,
                         RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long id)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        User user = userService.findById(id).orElseThrow();
        Post saved = postService.create(user.getName(), title, content, category);
        return "redirect:/community/" + saved.getId();
    }

    @PostMapping("/{id}/comments")
    public String addComment(@PathVariable Long id,
                             @RequestParam String content,
                             HttpSession session,
                             RedirectAttributes ra) {
        Object userId = session.getAttribute(SessionConst.LOGIN_USER_ID);
        if (!(userId instanceof Long uid)) {
            ra.addFlashAttribute("flash", "로그인이 필요합니다.");
            return "redirect:/login";
        }
        User user = userService.findById(uid).orElseThrow();
        postService.addComment(id, user.getName(), content);
        return "redirect:/community/" + id;
    }

    @PostMapping("/{id}/like")
    public String like(@PathVariable Long id) {
        postService.like(id);
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
            postService.deletePost(id, user.getName());
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
            postService.deleteComment(commentId, user.getName());
        } catch (SecurityException e) {
            ra.addFlashAttribute("flash", e.getMessage());
        }
        return "redirect:/community/" + postId;
    }
}
