package com.mindlink.service;

import com.mindlink.domain.User;
import com.mindlink.domain.UserRole;
import com.mindlink.dto.UserUpdateRequest;
import com.mindlink.chatcluster.UserAssessmentProfileRepository;
import com.mindlink.repository.ActivityLogRepository;
import com.mindlink.repository.AssessmentResultRepository;
import com.mindlink.repository.BookReviewRepository;
import com.mindlink.repository.BookingRepository;
import com.mindlink.repository.ReportRepository;
import com.mindlink.repository.UserAlertRepository;
import com.mindlink.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAlertRepository userAlertRepository;
    private final AssessmentResultRepository assessmentResultRepository;
    private final BookingRepository bookingRepository;
    private final ActivityLogRepository activityLogRepository;
    private final BookReviewRepository bookReviewRepository;
    private final ReportRepository reportRepository;
    private final UserAssessmentProfileRepository userAssessmentProfileRepository;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserAlertRepository userAlertRepository,
                       AssessmentResultRepository assessmentResultRepository,
                       BookingRepository bookingRepository,
                       ActivityLogRepository activityLogRepository,
                       BookReviewRepository bookReviewRepository,
                       ReportRepository reportRepository,
                       UserAssessmentProfileRepository userAssessmentProfileRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userAlertRepository = userAlertRepository;
        this.assessmentResultRepository = assessmentResultRepository;
        this.bookingRepository = bookingRepository;
        this.activityLogRepository = activityLogRepository;
        this.bookReviewRepository = bookReviewRepository;
        this.reportRepository = reportRepository;
        this.userAssessmentProfileRepository = userAssessmentProfileRepository;
    }

    @Transactional
    public User signup(String name, String email, String rawPassword, boolean sensitiveConsent) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        User user = new User(name, email, passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.USER);
        user.setNotificationEnabled(false);
        user.setSensitiveDataConsent(sensitiveConsent);
        return userRepository.save(user);
    }

    public Optional<User> login(String email, String rawPassword) {
        return userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPassword()));
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public void changeRole(Long userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setRole(newRole);
    }

    @Transactional
    public void updateSensitiveConsent(Long userId, boolean consent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setSensitiveDataConsent(consent);

        if (!consent) {
            // 동의 철회 시 자가진단 관련 데이터 즉시 파기
            userAlertRepository.deleteByUser(user);
            assessmentResultRepository.deleteByUser(user);
            userAssessmentProfileRepository.findByUserId(userId)
                    .ifPresent(userAssessmentProfileRepository::delete);
        }
    }

    public boolean checkPassword(Long userId, String rawPassword) {
        return userRepository.findById(userId)
                .map(u -> passwordEncoder.matches(rawPassword, u.getPassword()))
                .orElse(false);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 연관 데이터 삭제 (FK 의존성 높은 것 먼저)
        userAlertRepository.deleteByUser(user);
        assessmentResultRepository.deleteByUser(user);
        activityLogRepository.deleteByUser(user);
        bookingRepository.deleteByUser(user);
        bookReviewRepository.deleteByUser(user);
        reportRepository.deleteByReporter(user);

        // 개인정보 익명화 (통계 무결성 유지 목적으로 완전 삭제 대신 익명화)
        user.setName("탈퇴회원");
        user.setEmail("withdrawn_" + userId + "@deleted.invalid");
        user.setPassword("(withdrawn)");
        user.setNickname(null);
        user.setPhone(null);
        user.setRegion(null);
        user.setProfileImageUrl(null);
        user.setSensitiveDataConsent(false);
    }

    @Transactional
    public User updateProfile(Long userId, UserUpdateRequest req, String newProfileImageUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        if (!user.getEmail().equals(req.getEmail()) && userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        user.setName(req.getName());
        user.setEmail(req.getEmail());
        user.setNickname(req.getNickname());
        user.setRegion(req.getRegion());
        user.setNotificationEnabled(req.getNotificationEnabled());
        user.setPhone(req.getPhone());
        if (newProfileImageUrl != null) {
            user.setProfileImageUrl(newProfileImageUrl);
        }
        return user;
    }
}
