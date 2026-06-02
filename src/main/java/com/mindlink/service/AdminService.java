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
        // 이 사용자가 상담사로 담당하던 예약은 삭제하지 않고 배정만 해제(예약자는 다른 사람일 수 있음)
        bookingRepository.clearCounselor(user);
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

    /** 관리자 전체 예약 목록(최신순). status가 주어지면 해당 상태만 조회. */
    public List<Booking> allBookings(Booking.Status status) {
        return status == null
                ? bookingRepository.findAllByOrderByCreatedAtDesc()
                : bookingRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    /** 관리자 예약 상태 변경. */
    @Transactional
    public void changeBookingStatus(Long bookingId, Booking.Status status) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));
        booking.changeStatus(status);
    }

    public List<PostComment> commentsByUser(User user) {
        return commentRepository.findByAuthorOrderByCreatedAtDesc(user.getName());
    }
}
