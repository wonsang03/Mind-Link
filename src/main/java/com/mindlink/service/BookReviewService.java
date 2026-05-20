package com.mindlink.service;

import com.mindlink.domain.BookReview;
import com.mindlink.domain.User;
import com.mindlink.repository.BookReviewRepository;
import com.mindlink.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class BookReviewService {

    private final BookReviewRepository reviewRepo;
    private final UserRepository userRepo;

    public BookReviewService(BookReviewRepository reviewRepo, UserRepository userRepo) {
        this.reviewRepo = reviewRepo;
        this.userRepo   = userRepo;
    }

    @Transactional(readOnly = true)
    public List<BookReview> getReviews(String bookLink) {
        return reviewRepo.findByBookLinkOrderByCreatedAtDesc(bookLink);
    }

    @Transactional(readOnly = true)
    public Optional<BookReview> getMyReview(Long userId, String bookLink) {
        return reviewRepo.findByUserIdAndBookLink(userId, bookLink);
    }

    public BookReview save(Long userId, String bookLink, String bookTitle, int rating, String content) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        String safeLink = bookLink != null && bookLink.length() > 500
                ? bookLink.substring(0, 500) : bookLink;
        BookReview review = reviewRepo.findByUserIdAndBookLink(userId, safeLink)
                .orElseGet(BookReview::new);
        review.setUser(user);
        review.setBookLink(safeLink);
        review.setBookTitle(bookTitle != null && bookTitle.length() > 500
                ? bookTitle.substring(0, 500) : bookTitle);
        review.setRating(Math.min(5, Math.max(1, rating)));
        review.setContent(content != null ? content.trim() : "");
        return reviewRepo.save(review);
    }

    public void delete(Long userId, Long reviewId) {
        BookReview review = reviewRepo.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다"));
        if (!review.getUser().getId().equals(userId)) {
            throw new IllegalStateException("본인 리뷰만 삭제할 수 있습니다");
        }
        reviewRepo.delete(review);
    }
}
