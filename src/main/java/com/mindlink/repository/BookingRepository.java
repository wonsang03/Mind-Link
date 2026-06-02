package com.mindlink.repository;

import com.mindlink.domain.Booking;
import com.mindlink.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserOrderByCreatedAtDesc(User user);
    long countByUser(User user);
    void deleteByUser(User user);

    // 관리자 전체 예약 목록(최신순) / 상태별 필터 조회
    List<Booking> findAllByOrderByCreatedAtDesc();
    List<Booking> findByStatusOrderByCreatedAtDesc(Booking.Status status);

    // 상담사 배정: 미배정 풀 / 본인 담당
    List<Booking> findByCounselorIsNullOrderByCreatedAtDesc();
    List<Booking> findByCounselorOrderByCreatedAtDesc(User counselor);

    /** 상담사 계정 삭제 시 담당 배정만 해제(예약 자체는 보존). */
    @Modifying
    @Query("update Booking b set b.counselor = null where b.counselor = :counselor")
    void clearCounselor(@Param("counselor") User counselor);

    @Query("select distinct b.user from Booking b where b.user is not null")
    List<User> findDistinctUsers();
}
