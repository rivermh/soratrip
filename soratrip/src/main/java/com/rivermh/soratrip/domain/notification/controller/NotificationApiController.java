package com.rivermh.soratrip.domain.notification.controller;

import com.rivermh.soratrip.domain.notification.dto.NotificationResponseDto;
import com.rivermh.soratrip.domain.notification.service.NotificationService;
import com.rivermh.soratrip.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationApiController {

	private final NotificationService notificationService;

	// 최근 알림 목록 (최대 30건)
	@GetMapping
	public List<NotificationResponseDto> getRecent(Authentication authentication) {
		String email = SecurityUtils.requireEmail(authentication);
		return notificationService.getRecent(email);
	}

	// 읽지 않은 알림 수
	@GetMapping("/unread-count")
	public Map<String, Long> getUnreadCount(Authentication authentication) {
		String email = SecurityUtils.requireEmail(authentication);
		return Map.of("count", notificationService.getUnreadCount(email));
	}

	// 전체 읽음 처리
	@PostMapping("/read-all")
	public ResponseEntity<Void> markAllAsRead(Authentication authentication) {
		String email = SecurityUtils.requireEmail(authentication);
		notificationService.markAllAsRead(email);
		return ResponseEntity.ok().build();
	}
}
