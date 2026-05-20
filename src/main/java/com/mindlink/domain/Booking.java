package com.mindlink.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "bookings")
public class Booking {

    public enum Status { REQUESTED, CONFIRMED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "center_name", nullable = false, length = 200)
    private String centerName;

    @Column(name = "center_title", length = 100)
    private String centerTitle;

    @Column(name = "center_phone", length = 30)
    private String centerPhone;

    @Column(name = "center_address", length = 300)
    private String centerAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "booking_date", nullable = false)
    private LocalDate date;

    @Column(name = "booking_time", nullable = false)
    private LocalTime time;

    @Column(length = 50)
    private String type;

    @Lob
    @Column
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REQUESTED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = Status.REQUESTED;
    }

    public Booking() {}

    public Booking(String centerName, String centerTitle, String centerPhone, String centerAddress,
                   User user, String name, String phone, String email,
                   LocalDate date, LocalTime time, String type, String memo) {
        this.centerName = centerName;
        this.centerTitle = centerTitle;
        this.centerPhone = centerPhone;
        this.centerAddress = centerAddress;
        this.user = user;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.date = date;
        this.time = time;
        this.type = type;
        this.memo = memo;
    }

    public Long getId() { return id; }
    public String getCenterName() { return centerName; }
    public String getCenterTitle() { return centerTitle; }
    public String getCenterPhone() { return centerPhone; }
    public String getCenterAddress() { return centerAddress; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public LocalDate getDate() { return date; }
    public LocalTime getTime() { return time; }
    public String getType() { return type; }
    public String getMemo() { return memo; }
    public Status getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void cancel() { this.status = Status.CANCELLED; }
    public void confirm() { this.status = Status.CONFIRMED; }
}
