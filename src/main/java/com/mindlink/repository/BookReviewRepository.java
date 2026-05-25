package com.mindlink.repository;

import com.mindlink.domain.BookReview;
import com.mindlink.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookReviewRepository extends JpaRepository<BookReview, Long> {
    List<BookReview> findByBookLinkOrderByCreatedAtDesc(String bookLink);
    Optional<BookReview> findByUserIdAndBookLink(Long userId, String bookLink);
    void deleteByUser(User user);
}
