package com.mindlink.repository;

import com.mindlink.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByOrderByCreatedAtDesc();
    List<Post> findByCategoryOrderByCreatedAtDesc(String category);
    List<Post> findByAuthorOrderByCreatedAtDesc(String author);
    long countByAuthor(String author);

    /** 동일 군집 인기글(C-1) — 작성자명 집합으로 인기순 조회 (author=users.name, FK 아님) */
    List<Post> findByAuthorInOrderByLikesDescCreatedAtDesc(Collection<String> authors);
}
