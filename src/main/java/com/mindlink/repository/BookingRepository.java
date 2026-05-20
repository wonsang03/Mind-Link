package com.mindlink.repository;

import com.mindlink.domain.Booking;
import com.mindlink.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserOrderByCreatedAtDesc(User user);
}
