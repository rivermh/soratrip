package com.rivermh.soratrip.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class NotificationResponseDto {
	private Long id;
	private String message;
	private String link;
	private boolean read;
	private LocalDateTime createdAt;
}
