package com.mindlink.dto;

/** 관리자 대시보드 통계 카드용 집계 값. */
public record AdminStats(
        long totalUsers,
        long adminCount,
        long counselorCount,
        long userCount,
        long postCount,
        long commentCount,
        long reportCount,
        long bookingCount,
        long assessmentCount,
        long noticeCount,
        long highRiskUserCount
) {}
