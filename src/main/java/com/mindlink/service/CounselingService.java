package com.mindlink.service;

import com.mindlink.domain.Booking;
import com.mindlink.domain.User;
import com.mindlink.dto.CenterResponse;
import com.mindlink.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CounselingService {

    private final NaverLocalSearchClient naverClient;
    private final BookingRepository bookingRepository;

    public CounselingService(NaverLocalSearchClient naverClient,
                             BookingRepository bookingRepository) {
        this.naverClient = naverClient;
        this.bookingRepository = bookingRepository;
    }

    public List<CenterResponse> searchCenters(String query) {
        return naverClient.search(query);
    }

    /** 화면에 표시·네이버 API에 쓰는 실제 검색어 */
    public String effectiveSearchQuery(String query) {
        return naverClient.normalizeQuery(query);
    }

    public boolean isExternalApiConfigured() {
        return naverClient.isConfigured();
    }

    public Optional<Booking> findBooking(Long id) {
        return bookingRepository.findById(id);
    }

    public List<Booking> findBookingsByUser(User user) {
        if (user == null) return List.of();
        return bookingRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public Booking createBooking(String centerName, String centerTitle, String centerPhone, String centerAddress,
                                 User user, String name, String phone, String email,
                                 LocalDate date, LocalTime time, String type, String memo) {
        if (centerName == null || centerName.isBlank()) {
            throw new IllegalArgumentException("상담 센터 정보가 누락되었습니다.");
        }
        if (date == null || date.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("희망 날짜는 오늘 이후여야 합니다.");
        }
        Booking booking = new Booking(centerName, centerTitle, centerPhone, centerAddress,
                user, name, phone, email, date, time, type, memo);
        return bookingRepository.save(booking);
    }

    @Transactional
    public void cancelBooking(Long bookingId, User user) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));
        if (booking.getUser() == null || user == null || !booking.getUser().getId().equals(user.getId())) {
            throw new SecurityException("본인이 신청한 예약만 취소할 수 있습니다.");
        }
        booking.cancel();
    }
}
