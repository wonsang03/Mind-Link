package com.mindlink.dto;

import com.mindlink.domain.Booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class BookingResponse {

    private final Long id;
    private final String centerName;
    private final String centerTitle;
    private final String centerPhone;
    private final String centerAddress;
    private final String name;
    private final String phone;
    private final String email;
    private final LocalDate date;
    private final LocalTime time;
    private final String type;
    private final String memo;
    private final String status;
    private final LocalDateTime createdAt;

    public BookingResponse(Booking b) {
        this.id = b.getId();
        this.centerName = b.getCenterName();
        this.centerTitle = b.getCenterTitle();
        this.centerPhone = b.getCenterPhone();
        this.centerAddress = b.getCenterAddress();
        this.name = b.getName();
        this.phone = b.getPhone();
        this.email = b.getEmail();
        this.date = b.getDate();
        this.time = b.getTime();
        this.type = b.getType();
        this.memo = b.getMemo();
        this.status = b.getStatus().name();
        this.createdAt = b.getCreatedAt();
    }

    public Long getId() { return id; }
    public String getCenterName() { return centerName; }
    public String getCenterTitle() { return centerTitle; }
    public String getCenterPhone() { return centerPhone; }
    public String getCenterAddress() { return centerAddress; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public LocalDate getDate() { return date; }
    public LocalTime getTime() { return time; }
    public String getType() { return type; }
    public String getMemo() { return memo; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
