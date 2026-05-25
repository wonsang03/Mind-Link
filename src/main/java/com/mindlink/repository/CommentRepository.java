package com.mindlink.repository;

import com.mindlink.domain.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<PostComment, Long> {

    List<PostComment> findByAuthorOrderByCreatedAtDesc(String author);

    List<PostComment> findByPost_IdOrderByCreatedAtAsc(Long postId);
}
