package com.rivermh.soratrip.domain.member.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 실패 카운트 및 계정 잠금 상태를 Redis(TTL)로 관리하는 서비스
 * (MailService가 이메일 인증 Redis 상태를 한 곳에 모아두는 것과 동일한 스타일).
 */
@Service
@RequiredArgsConstructor
public class AuthRedisService {

    private final StringRedisTemplate redisTemplate;

    private static final String LOGIN_FAIL_PREFIX = "login-fail:";
    private static final String LOGIN_LOCK_PREFIX = "login-lock:";

    private static final int LOGIN_FAIL_THRESHOLD = 5;
    private static final long LOGIN_FAIL_WINDOW_SECONDS = 900;  // 15분
    private static final long LOGIN_LOCK_DURATION_SECONDS = 30; // 30초

    public boolean isLocked(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(LOGIN_LOCK_PREFIX + email));
    }

    // 로그인 실패 시 호출. 5회 누적되면 잠금 키를 설정한다.
    public void recordLoginFailure(String email) {
        String key = LOGIN_FAIL_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofSeconds(LOGIN_FAIL_WINDOW_SECONDS));
        }
        if (count != null && count >= LOGIN_FAIL_THRESHOLD) {
            redisTemplate.opsForValue().set(
                    LOGIN_LOCK_PREFIX + email,
                    "1",
                    Duration.ofSeconds(LOGIN_LOCK_DURATION_SECONDS)
            );
        }
    }

    // 로그인 성공 시 실패 카운트/잠금 상태를 즉시 초기화 (TTL 만료를 기다리지 않음)
    public void clearLoginAttempts(String email) {
        redisTemplate.delete(LOGIN_FAIL_PREFIX + email);
        redisTemplate.delete(LOGIN_LOCK_PREFIX + email);
    }
}
