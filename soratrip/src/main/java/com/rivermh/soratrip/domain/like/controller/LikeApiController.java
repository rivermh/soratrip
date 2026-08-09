package com.rivermh.soratrip.domain.like.controller;

import com.rivermh.soratrip.domain.like.dto.LikeToggleResponse;
import com.rivermh.soratrip.domain.like.service.LikeService;
import com.rivermh.soratrip.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
        String email = SecurityUtils.requireEmail(authentication);
        LikeToggleResponse response = likeService.togglePostLike(postId, email);
        return ResponseEntity.ok(response);
    }

    // 여행 일정 좋아요 토글 API
    @PostMapping("/schedules/{scheduleId}/like")
    public ResponseEntity<LikeToggleResponse> toggleScheduleLike(
            @PathVariable Long scheduleId,
            Authentication authentication) {
        String email = SecurityUtils.requireEmail(authentication);
        LikeToggleResponse response = likeService.toggleScheduleLike(scheduleId, email);
        return ResponseEntity.ok(response);
    }
}