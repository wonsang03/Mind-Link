package com.mindlink.service;

import com.mindlink.domain.Booking;
import com.mindlink.domain.Post;
import com.mindlink.domain.PostComment;
import com.mindlink.domain.User;
import com.mindlink.domain.UserRole;
import com.mindlink.dto.AdminStats;
import com.mindlink.repository.BookReviewRepository;
import com.mindlink.repository.BookingRepository;
import com.mindlink.repository.CommentRepository;
import com.mindlink.repository.NoticeRepository;
import com.mindlink.repository.PostRepository;
import com.mindlink.repository.ReportRepository;
import com.mindlink.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final NoticeRepository noticeRepository;
    private final BookingRepository bookingRepository;
    private final BookReviewRepository bookReviewRepository;

    public AdminService(UserRepository userRepository,
                        PostRepository postRepository,
                        CommentRepository commentRepository,
                        ReportRepository reportRepository,
                        NoticeRepository noticeRepository,
                        BookingRepository bookingRepository,
                        BookReviewRepository bookReviewRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.noticeRepository = noticeRepository;
        this.bookingRepository = bookingRepository;
        this.bookReviewRepository = bookReviewRepository;
    }

    public AdminStats stats() {
        return new AdminStats(
                userRepository.count(),
                userRepository.countByRole(UserRole.ADMIN),
                userRepository.countByRole(UserRole.COUNSELOR),
                userRepository.countByRole(UserRole.USER),
                postRepository.count(),
                commentRepository.count(),
                reportRepository.count(),
                bookingRepository.count(),
                noticeRepository.count()
        );
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        bookingRepository.deleteByUser(user);
        reportRepository.deleteByReporter(user);
        bookReviewRepository.deleteByUser(user);
        userRepository.delete(user);
    }

    public List<Post> postsByUser(User user) {
        return postRepository.findByAuthorOrderByCreatedAtDesc(user.getName());
    }

    public List<Booking> bookingsByUser(User user) {
        return bookingRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public List<PostComment> commentsByUser(User user) {
        return commentRepository.findByAuthorOrderByCreatedAtDesc(user.getName());
    }
}
