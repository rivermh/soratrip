package com.rivermh.soratrip.domain.bookmark.controller;

import com.rivermh.soratrip.domain.bookmark.dto.BookmarkToggleResponse;
import com.rivermh.soratrip.domain.bookmark.service.BookmarkService;
import com.rivermh.soratrip.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
        String email = SecurityUtils.requireEmail(authentication);
        BookmarkToggleResponse response = bookmarkService.toggleScheduleBookmark(scheduleId, email);
        return ResponseEntity.ok(response);
    }
}