package com.mindlink.controller;

import com.mindlink.domain.Attachment;
import com.mindlink.domain.Post;
import com.mindlink.domain.PostComment;
import com.mindlink.domain.Report;
import com.mindlink.domain.User;
import com.mindlink.chatcluster.ClusterContentService;
import com.mindlink.dto.AttachmentResponse;
import com.mindlink.service.CommunityCategoryPreferenceService;
import com.mindlink.service.CommunityService;
import com.mindlink.service.FileStorageService;
import com.mindlink.service.ProverbService;
import com.mindlink.service.UserService;
import com.mindlink.web.SessionConst;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
    private final ClusterContentService clusterContentService;
    private final ProverbService proverbService;

    public CommunityController(CommunityService communityService,
                                UserService userService,
                                FileStorageService fileStorageService,
                                CommunityCategoryPreferenceService categoryPreferenceService,
                                ClusterContentService clusterContentService,
                                ProverbService proverbService) {
        this.communityService = communityService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
        this.categoryPreferenceService = categoryPreferenceService;
        this.clusterContentService = clusterContentService;
        this.proverbService = proverbService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String sort,
                       HttpSession session,
                       Model model) {
        // 로그인 유저 맞춤 카테고리 추론
        Object userIdAttr = session.getAttribute(SessionConst.LOGIN_USER_ID);
        Long uid = (userIdAttr instanceof Long l) ? l : null;
        List<String> preferredCategories = categoryPreferenceService.resolvePreferredCategories(uid);
        // 기본은 항상 "전체" — 프로필이 있으면 아래 맞춤 정렬(관심도×최신성)이 적용되어
        // 단일 카테고리에 가두지 않고 우선순위 순서대로 보여 준다. (가장 위험한 분야만 들이미는 부담도 완화)
        String selectedCategory = (category != null && !category.isBlank()) ? category : "전체";

        List<Post> posts = communityService.findAll(selectedCategory);
        if (q != null && !q.isBlank()) {
            String query = q.toLowerCase();
            posts = posts.stream()
                    .filter(p -> p.getTitle().toLowerCase().contains(query)
                            || p.getContent().toLowerCase().contains(query))
                    .toList();
        }

        // 정렬: popular(기본·인기순) / recent(최신순) / likes(좋아요순)
        //   - recent : 시간순 (findAll 이 이미 최신순)
        //   - likes  : 좋아요 많은 순 (동점 시 최신)
        //   - popular: 프로필 있으면 "관심도×최신성" 그라데이션, 없으면 "좋아요×최신성" 인기 블렌드
        String activeSort = (sort == null || sort.isBlank()) ? "popular" : sort;
        if ("recent".equals(activeSort)) {
            // 이미 최신순 — 변경 없음
        } else if ("likes".equals(activeSort)) {
            posts = posts.stream()
                    .sorted(Comparator.comparingInt(Post::getLikes)
                            .thenComparing(Post::getCreatedAt).reversed())
                    .toList();
        } else { // popular (기본)
            activeSort = "popular";
            if (posts.size() > 1) {
                Map<String, Double> categoryAffinity = categoryPreferenceService.resolveCategoryAffinity(uid);
                boolean profiled = !categoryAffinity.isEmpty();
                final double PRIMARY_WEIGHT = 0.6;
                final double RECENCY_WEIGHT = 0.4;
                final double NEUTRAL_AFFINITY = 0.15;   // 축 없는 카테고리(인간관계·일상 등)
                final int n = posts.size();
                final int maxLikes = Math.max(1, posts.stream().mapToInt(Post::getLikes).max().orElse(0));
                // findAll 이 최신순 → 현재 인덱스가 곧 최신 순위 (0 = 가장 최신)
                IdentityHashMap<Post, Integer> recencyRank = new IdentityHashMap<>();
                for (int i = 0; i < n; i++) recencyRank.put(posts.get(i), i);
                posts = posts.stream()
                        .sorted(Comparator.comparingDouble((Post p) -> {
                            double primary = profiled
                                    ? categoryAffinity.getOrDefault(p.getCategory(), NEUTRAL_AFFINITY)
                                    : (p.getLikes() / (double) maxLikes);
                            double recency = 1.0 - (recencyRank.get(p) / (double) (n - 1));
                            return PRIMARY_WEIGHT * primary + RECENCY_WEIGHT * recency;
                        }).reversed())
                        .toList();
            }
        }

        // C-1: 같은 정서 군집 실사용자가 많이 본 글 (로그인 + 프로필 + 검색 아님)
        if (uid != null && (q == null || q.isBlank())) {
            ClusterContentService.Result clusterPopular =
                    clusterContentService.popularPostsForUserCluster(uid, 5);
            if (clusterPopular != null) {
                model.addAttribute("clusterPopularPosts", clusterPopular.posts());
                model.addAttribute("clusterPopularLabel", clusterPopular.clusterLabel());
            }
        }

        model.addAttribute("posts", posts);
        model.addAttribute("categories", CATEGORIES);
        model.addAttribute("selectedCategory", selectedCategory);
        model.addAttribute("sort", activeSort);
        model.addAttribute("query", q);
        model.addAttribute("preferredCategories", preferredCategories);
        model.addAttribute("highlightCategory", preferredCategories.isEmpty() ? null : preferredCategories.get(0));
        model.addAttribute("proverb", proverbService.getRandom("COMMUNITY"));
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
