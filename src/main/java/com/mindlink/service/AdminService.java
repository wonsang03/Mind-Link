package com.mindlink.service;

import com.mindlink.domain.AssessmentResult;
import com.mindlink.domain.Booking;
import com.mindlink.domain.Post;
import com.mindlink.domain.User;
import com.mindlink.domain.UserRole;
import com.mindlink.dto.AdminStats;
import com.mindlink.dto.ManagedUserView;
import com.mindlink.repository.AssessmentResultRepository;
import com.mindlink.repository.BookReviewRepository;
import com.mindlink.repository.BookingRepository;
import com.mindlink.repository.CommentRepository;
import com.mindlink.repository.NoticeRepository;
import com.mindlink.repository.PostRepository;
import com.mindlink.repository.ReportRepository;
import com.mindlink.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 관리자/상담사 뷰의 통계 집계와 "관리 대상 유저"(상담 이력 또는 고위험 자가진단) 분류를 담당합니다.
 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final NoticeRepository noticeRepository;
    private final BookingRepository bookingRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final BookReviewRepository bookReviewRepository;

    public AdminService(UserRepository userRepository,
                        PostRepository postRepository,
                        CommentRepository commentRepository,
                        ReportRepository reportRepository,
                        NoticeRepository noticeRepository,
                        BookingRepository bookingRepository,
                        AssessmentResultRepository assessmentResultRepository,
                        BookReviewRepository bookReviewRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.noticeRepository = noticeRepository;
        this.bookingRepository = bookingRepository;
        this.assessmentResultRepository = assessmentResultRepository;
        this.bookReviewRepository = bookReviewRepository;
    }

    /**
     * 유저 계정을 삭제합니다. users 를 참조하는 종속 데이터(자가진단·예약·신고·도서리뷰)를
     * 먼저 정리한 뒤 삭제합니다. (게시글은 작성자 이름 문자열이라 그대로 남습니다.)
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        assessmentResultRepository.deleteByUser(user);
        bookingRepository.deleteByUser(user);
        reportRepository.deleteByReporter(user);
        bookReviewRepository.deleteByUser(user);
        userRepository.delete(user);
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
                assessmentResultRepository.count(),
                noticeRepository.count(),
                assessmentResultRepository.findHighRiskUsers().size()
        );
    }

    /** 고위험 자가진단 이력 또는 상담 예약 이력이 있는 유저 목록(요약). */
    public List<ManagedUserView> findManagedUsers() {
        Set<User> highRiskUsers = new LinkedHashSet<>(assessmentResultRepository.findHighRiskUsers());
        Set<User> bookedUsers = new LinkedHashSet<>(bookingRepository.findDistinctUsers());

        Set<User> managed = new LinkedHashSet<>();
        managed.addAll(highRiskUsers);
        managed.addAll(bookedUsers);

        return managed.stream()
                .map(u -> toView(u, highRiskUsers.contains(u), bookedUsers.contains(u)))
                .sorted(Comparator
                        .comparing(ManagedUserView::highRisk).reversed()
                        .thenComparing(v -> v.latestAssessmentAt() == null
                                ? java.time.LocalDateTime.MIN : v.latestAssessmentAt(),
                                Comparator.reverseOrder()))
                .toList();
    }

    private ManagedUserView toView(User user, boolean highRisk, boolean hasCounselingHistory) {
        List<AssessmentResult> results = assessmentResultRepository.findByUserOrderByCreatedAtDesc(user);
        AssessmentResult latest = results.isEmpty() ? null : results.get(0);
        return new ManagedUserView(
                user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getCreatedAt(),
                postRepository.countByAuthor(user.getName()),
                bookingRepository.countByUser(user),
                results.size(),
                latest == null ? null : latest.getLevel(),
                latest == null ? null : latest.getCreatedAt(),
                highRisk,
                hasCounselingHistory
        );
    }

    public List<Post> postsByUser(User user) {
        return postRepository.findByAuthorOrderByCreatedAtDesc(user.getName());
    }

    public List<AssessmentResult> assessmentsByUser(User user) {
        return assessmentResultRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public List<Booking> bookingsByUser(User user) {
        return bookingRepository.findByUserOrderByCreatedAtDesc(user);
    }
}
