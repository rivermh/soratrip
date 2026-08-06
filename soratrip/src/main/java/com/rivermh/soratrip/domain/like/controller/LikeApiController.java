package com.rivermh.soratrip.domain.like.controller;

import com.rivermh.soratrip.domain.like.dto.LikeToggleResponse;
import com.rivermh.soratrip.domain.like.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LikeApiController {

    private final LikeService likeService;

    // 게시글 좋아요 토글 API
    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<LikeToggleResponse> togglePostLike(
    		@PathVariable("postId") Long postId,
            Authentication authentication) {
        String email = extractEmail(authentication);
        LikeToggleResponse response = likeService.togglePostLike(postId, email);
        return ResponseEntity.ok(response);
    }

    // 여행 일정 좋아요 토글 API
    @PostMapping("/schedules/{scheduleId}/like")
    public ResponseEntity<LikeToggleResponse> toggleScheduleLike(
            @PathVariable Long scheduleId,
            Authentication authentication) {
        String email = extractEmail(authentication);
        LikeToggleResponse response = likeService.toggleScheduleLike(scheduleId, email);
        return ResponseEntity.ok(response);
    }

    // Authentication 객체에서 일반/OAuth2 사용자의 이메일 공통 추출 함수
    private String extractEmail(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername(); // 일반 로그인 이메일
        } else if (principal instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email"); // OAuth2 로그인 이메일
        }
        throw new IllegalArgumentException("인증 정보를 찾을 수 없습니다.");
    }
}