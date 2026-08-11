package com.rivermh.soratrip.domain.post.service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * 짧은 시간 안에 같은 사람이 새로고침/재방문할 때마다 조회수가 계속 올라가는 것을 막기 위한
 * Redis 기반 중복조회 방지 서비스 (AuthRedisService가 로그인 실패 상태를 Redis TTL로
 * 관리하는 것과 동일한 스타일).
 */
@Service
@RequiredArgsConstructor
public class PostViewRedisService {

    private final StringRedisTemplate redisTemplate;

    private static final String VIEW_PREFIX = "post-view:";
    private static final long VIEW_TTL_SECONDS = 86400; // 24시간

    // 최근에 조회한 기록이 있으면 true, 없으면(=이번이 첫 조회) 기록을 남기고 false 반환
    public boolean isRecentlyViewed(Long postId, String viewerKey) {
        String key = VIEW_PREFIX + postId + ":" + viewerKey;
        Boolean firstView = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(VIEW_TTL_SECONDS));
        return !Boolean.TRUE.equals(firstView);
    }
}
