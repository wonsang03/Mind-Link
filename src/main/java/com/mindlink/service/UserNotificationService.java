package com.mindlink.service;

import com.mindlink.domain.Notice;
import com.mindlink.domain.Post;
import com.mindlink.domain.PostComment;
import com.mindlink.domain.User;
import com.mindlink.domain.UserAlert;
import com.mindlink.domain.UserRole;
import com.mindlink.repository.CommentRepository;
import com.mindlink.repository.UserAlertRepository;
import com.mindlink.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class UserNotificationService {

    private final UserAlertRepository alertRepo;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;

    public UserNotificationService(UserAlertRepository alertRepo,
                                   UserRepository userRepository,
                                   CommentRepository commentRepository) {
        this.alertRepo = alertRepo;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
    }

    @Transactional
    public void onPostComment(Post post, PostComment comment, String commenterDisplayName) {
        if (post == null || comment == null || commenterDisplayName == null) return;
        String excerpt = truncate(comment.getContent(), 80);

        if (comment.getParentComment() != null) {
            PostComment parent = comment.getParentComment();
            if (!commenterDisplayName.equals(parent.getAuthor())) {
                notifyByDisplayName(
                        parent.getAuthor(),
                        commenterDisplayName,
                        "COMMENT_REPLY",
                        commenterDisplayName + "님이 회원님 댓글에 답글을 남겼어요: " + excerpt,
                        "/community/" + post.getId() + "#comment-" + comment.getId(),
                        post.getId(),
                        comment.getId(),
                        null
                );
            }
            return;
        }

        if (!commenterDisplayName.equals(post.getAuthor())) {
            notifyByDisplayName(
                    post.getAuthor(),
                    commenterDisplayName,
                    "POST_COMMENT",
                    commenterDisplayName + "님이 회원님 게시글에 댓글을 남겼어요: " + excerpt,
                    "/community/" + post.getId(),
                    post.getId(),
                    comment.getId(),
                    null
            );
        }

        Set<String> notified = new HashSet<>();
        notified.add(commenterDisplayName);
        notified.add(post.getAuthor());
        for (PostComment c : commentRepository.findByPost_IdOrderByCreatedAtAsc(post.getId())) {
            if (c.getId() != null && c.getId().equals(comment.getId())) continue;
            String author = c.getAuthor();
            if (author == null || notified.contains(author)) continue;
            notified.add(author);
            notifyByDisplayName(
                    author,
                    commenterDisplayName,
                    "POST_COMMENT",
                    "관심 게시글에 " + commenterDisplayName + "님이 새 댓글을 남겼어요: " + excerpt,
                    "/community/" + post.getId(),
                    post.getId(),
                    comment.getId(),
                    null
            );
        }
    }

    @Transactional
    public int notifyNewNotice(Notice notice) {
        if (notice == null) return 0;
        String msg = "새 공지: " + notice.getTitle();
        String link = "/notice/" + notice.getId();
        int count = 0;
        for (User u : userRepository.findAll()) {
            if (u.getRole() == UserRole.ADMIN) continue;
            createAlert(u, "NOTICE", msg, null, link, null, null, notice.getId());
            count++;
        }
        return count;
    }

    /** 관리자 알림 — 제목·본문·선택 링크 */
    @Transactional
    public int sendAdminMessage(Long userId, String title, String message,
                              boolean includeLink, String linkUrl) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("알림 내용을 입력해 주세요.");
        }
        String body = message.trim();
        String titleVal = blankToNull(title);
        String link = includeLink ? normalizeLink(linkUrl) : null;
        if (includeLink && link == null) {
            throw new IllegalArgumentException("바로가기 링크를 입력해 주세요. (예: /community/1)");
        }
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            createAlert(user, "ADMIN_MESSAGE", body, titleVal, link, null, null, null);
            return 1;
        }
        int count = 0;
        for (User u : userRepository.findAll()) {
            if (u.getRole() == UserRole.ADMIN) continue;
            createAlert(u, "ADMIN_MESSAGE", body, titleVal, link, null, null, null);
            count++;
        }
        return count;
    }

    private void notifyByDisplayName(String recipientDisplayName, String senderDisplayName,
                                     String type, String message, String linkUrl,
                                     Long postId, Long commentId, Long noticeId) {
        if (recipientDisplayName == null || recipientDisplayName.equals(senderDisplayName)) return;
        findUserByDisplayName(recipientDisplayName).ifPresent(u ->
                createAlert(u, type, message, null, linkUrl, postId, commentId, noticeId));
    }

    private Optional<User> findUserByDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) return Optional.empty();
        Optional<User> byName = userRepository.findFirstByName(displayName.trim());
        if (byName.isPresent()) return byName;
        return userRepository.findFirstByNickname(displayName.trim());
    }

    private void createAlert(User user, String type, String message, String title, String linkUrl,
                             Long postId, Long commentId, Long noticeId) {
        UserAlert alert = new UserAlert();
        alert.setUser(user);
        alert.setAlertType(type);
        alert.setMessage(message);
        alert.setTitle(title);
        alert.setLinkUrl(linkUrl);
        alert.setRelatedPostId(postId);
        alert.setRelatedCommentId(commentId);
        alert.setNoticeId(noticeId);
        alert.setRead(false);
        alert.setCreatedAt(LocalDateTime.now());
        alertRepo.save(alert);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        if (t.length() <= max) return t;
        return t.substring(0, max) + "…";
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    private static String normalizeLink(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (!t.startsWith("/")) {
            t = "/" + t;
        }
        return t;
    }
}
