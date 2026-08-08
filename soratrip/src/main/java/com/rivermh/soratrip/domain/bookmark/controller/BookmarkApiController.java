package com.rivermh.soratrip.domain.bookmark.controller;

import com.rivermh.soratrip.domain.bookmark.dto.BookmarkToggleResponse;
import com.rivermh.soratrip.domain.bookmark.service.BookmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BookmarkApiController {

    private final BookmarkService bookmarkService;

    // 여행 일정 북마크 토글 API (Spring Boot 3 파라미터 명시)
    @PostMapping("/schedules/{scheduleId}/bookmark")
    public ResponseEntity<BookmarkToggleResponse> toggleScheduleBookmark(
            @PathVariable("scheduleId") Long scheduleId,
            Authentication authentication) {
        String email = extractEmail(authentication);
        BookmarkToggleResponse response = bookmarkService.toggleScheduleBookmark(scheduleId, email);
        return ResponseEntity.ok(response);
    }

    private String extractEmail(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        } else if (principal instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        }
        throw new IllegalArgumentException("인증 정보를 찾을 수 없습니다.");
    }
}