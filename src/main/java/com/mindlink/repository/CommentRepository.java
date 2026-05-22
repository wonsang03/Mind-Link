package com.mindlink.repository;

import com.mindlink.domain.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<PostComment, Long> {
}
