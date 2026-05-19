package com.mindlink.service;

import com.mindlink.domain.Attachment;
import com.mindlink.domain.Post;
import com.mindlink.domain.PostComment;
import com.mindlink.domain.Report;
import com.mindlink.domain.User;
import com.mindlink.repository.CommentRepository;
import com.mindlink.repository.PostRepository;
import com.mindlink.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Service
public class CommunityService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final FileStorageService fileStorageService;

    public CommunityService(PostRepository postRepository,
                             CommentRepository commentRepository,
                             ReportRepository reportRepository,
                             FileStorageService fileStorageService) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.fileStorageService = fileStorageService;
    }

    public List<Post> findAll(String category) {
        if (category == null || category.isBlank() || "전체".equals(category)) {
            return postRepository.findAllByOrderByCreatedAtDesc();
        }
        return postRepository.findByCategoryOrderByCreatedAtDesc(category);
    }

    public Optional<Post> findById(Long id) {
        return postRepository.findById(id);
    }

    @Transactional
    public Post create(String author, String title, String content, String category,
                       MultipartFile[] files, String[] linkUrls) {
        Post post = new Post(author, title, content, category);
        Post saved = postRepository.save(post);
        fileStorageService.saveFiles(files, Attachment.TargetType.POST, saved.getId());
        fileStorageService.saveLinks(linkUrls, Attachment.TargetType.POST, saved.getId());
        return saved;
    }

    @Transactional
    public Post updatePost(Long postId, String currentUserName,
                           String title, String content, String category,
                           List<Long> removeAttachmentIds, MultipartFile[] newFiles,
                           String[] newLinkUrls) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        if (!post.getAuthor().equals(currentUserName)) {
            throw new SecurityException("본인이 작성한 게시글만 수정할 수 있습니다.");
        }
        post.setTitle(title);
        post.setContent(content);
        post.setCategory(category);

        if (removeAttachmentIds != null) {
            for (Long aid : removeAttachmentIds) {
                fileStorageService.deleteById(aid);
            }
        }
        fileStorageService.saveFiles(newFiles, Attachment.TargetType.POST, postId);
        fileStorageService.saveLinks(newLinkUrls, Attachment.TargetType.POST, postId);
        return post;
    }

    @Transactional
    public PostComment addComment(Long postId, String author, String content,
                                   MultipartFile[] files, String[] linkUrls) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        PostComment comment = new PostComment(post, author, content);
        PostComment saved = commentRepository.save(comment);
        fileStorageService.saveFiles(files, Attachment.TargetType.COMMENT, saved.getId());
        fileStorageService.saveLinks(linkUrls, Attachment.TargetType.COMMENT, saved.getId());
        return saved;
    }

    @Transactional
    public void like(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        post.incrementLikes();
    }

    @Transactional
    public void deletePost(Long postId, String currentUserName) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        if (!post.getAuthor().equals(currentUserName)) {
            throw new SecurityException("본인이 작성한 게시글만 삭제할 수 있습니다.");
        }
        for (PostComment c : post.getComments()) {
            fileStorageService.deleteByTarget(Attachment.TargetType.COMMENT, c.getId());
        }
        fileStorageService.deleteByTarget(Attachment.TargetType.POST, postId);
        postRepository.delete(post);
    }

    @Transactional
    public Long deleteComment(Long commentId, String currentUserName) {
        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다."));
        if (!comment.getAuthor().equals(currentUserName)) {
            throw new SecurityException("본인이 작성한 댓글만 삭제할 수 있습니다.");
        }
        Long postId = comment.getPost().getId();
        fileStorageService.deleteByTarget(Attachment.TargetType.COMMENT, commentId);
        comment.getPost().getComments().remove(comment);
        commentRepository.delete(comment);
        return postId;
    }

    @Transactional
    public Report report(User reporter, Report.TargetType targetType, Long targetId, String reason) {
        Report report = new Report(reporter, targetType, targetId, reason);
        return reportRepository.save(report);
    }
}
