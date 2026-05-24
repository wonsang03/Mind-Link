package com.mindlink.dto;

import com.mindlink.domain.UserRole;

import java.time.LocalDateTime;

/** 상담사/관리자 뷰에서 관리 대상 유저를 한 줄 요약으로 보여주기 위한 값. */
public record ManagedUserView(
        Long id,
        String name,
        String email,
        UserRole role,
        LocalDateTime createdAt,
        long postCount,
        long bookingCount,
        long assessmentCount,
        String latestLevel,
        LocalDateTime latestAssessmentAt,
        boolean highRisk,
        boolean hasCounselingHistory
) {}
