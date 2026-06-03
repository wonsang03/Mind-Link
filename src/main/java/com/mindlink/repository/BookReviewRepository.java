package com.mindlink.repository;

import com.mindlink.domain.BookReview;
import com.mindlink.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface BookReviewRepository extends JpaRepository<BookReview, Long> {
    List<BookReview> findByBookLinkOrderByCreatedAtDesc(String bookLink);
    Optional<BookReview> findByUserIdAndBookLink(Long userId, String bookLink);
    void deleteByUser(User user);

    /** 추천 인기순 정렬용 — book_link 별 평균 별점·리뷰 수: [bookLink, avgRating, count] */
    @Query("SELECT r.bookLink, AVG(r.rating), COUNT(r) FROM BookReview r GROUP BY r.bookLink")
    List<Object[]> aggregateRatingByBookLink();
}
