package com.mindlink.service;

import com.mindlink.domain.Post;
import com.mindlink.domain.PostComment;
import com.mindlink.repository.PostCommentRepository;
import com.mindlink.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;

    public PostService(PostRepository postRepository, PostCommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
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
    public Post create(String author, String title, String content, String category) {
        Post post = new Post(author, title, content, category);
        return postRepository.save(post);
    }

    @Transactional
    public void addComment(Long postId, String author, String content) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        PostComment comment = new PostComment(post, author, content);
        commentRepository.save(comment);
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
        Post post = comment.getPost();
        post.getComments().remove(comment);
        commentRepository.delete(comment);
        return postId;
    }
}
