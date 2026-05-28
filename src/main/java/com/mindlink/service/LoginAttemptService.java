package com.mindlink.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP 기반 속도 제한 + 계정 기반 잠금을 인메모리로 관리한다.
 *
 * IP  : 1분 내 10회 초과 시 차단
 * 계정 : 5회 연속 실패 시 30분 잠금
 */
@Service
public class LoginAttemptService {

    private static final int    MAX_IP_ATTEMPTS       = 10;
    private static final Duration IP_WINDOW           = Duration.ofMinutes(1);

    private static final int    MAX_ACCOUNT_FAILURES  = 5;
    private static final Duration ACCOUNT_LOCK_DURATION = Duration.ofMinutes(30);

    // IP → 최근 시도 시각 목록
    private final ConcurrentHashMap<String, Deque<Instant>> ipAttempts = new ConcurrentHashMap<>();

    // email → 실패 횟수 + 잠금 해제 시각
    private final ConcurrentHashMap<String, AccountStatus> accountStatus = new ConcurrentHashMap<>();

    private static class AccountStatus {
        int failCount = 0;
        Instant lockedUntil = null;
    }

    // ── IP 속도 제한 ─────────────────────────────────────────────────────────

    public boolean isIpBlocked(String ip) {
        Deque<Instant> attempts = ipAttempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (attempts) {
            purgeOldEntries(attempts);
            return attempts.size() >= MAX_IP_ATTEMPTS;
        }
    }

    public void recordIpAttempt(String ip) {
        Deque<Instant> attempts = ipAttempts.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (attempts) {
            purgeOldEntries(attempts);
            attempts.addLast(Instant.now());
        }
    }

    private void purgeOldEntries(Deque<Instant> attempts) {
        Instant cutoff = Instant.now().minus(IP_WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.pollFirst();
        }
    }

    // ── 계정 잠금 ─────────────────────────────────────────────────────────────

    public boolean isAccountLocked(String email) {
        AccountStatus status = accountStatus.get(email);
        if (status == null || status.lockedUntil == null) return false;
        if (Instant.now().isAfter(status.lockedUntil)) {
            status.failCount = 0;
            status.lockedUntil = null;
            return false;
        }
        return true;
    }

    public Duration getAccountLockRemaining(String email) {
        AccountStatus status = accountStatus.get(email);
        if (status == null || status.lockedUntil == null) return Duration.ZERO;
        Duration remaining = Duration.between(Instant.now(), status.lockedUntil);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public void recordLoginFailure(String email) {
        AccountStatus status = accountStatus.computeIfAbsent(email, k -> new AccountStatus());
        synchronized (status) {
            status.failCount++;
            if (status.failCount >= MAX_ACCOUNT_FAILURES) {
                status.lockedUntil = Instant.now().plus(ACCOUNT_LOCK_DURATION);
            }
        }
    }

    public void recordLoginSuccess(String email) {
        accountStatus.remove(email);
    }
}
