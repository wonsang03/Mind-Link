package com.mindlink.repository;

import com.mindlink.domain.Booking;
import com.mindlink.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserOrderByCreatedAtDesc(User user);
    long countByUser(User user);
    void deleteByUser(User user);

    @Query("select distinct b.user from Booking b where b.user is not null")
    List<User> findDistinctUsers();
}
