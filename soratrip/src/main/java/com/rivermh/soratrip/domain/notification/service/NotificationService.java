package com.rivermh.soratrip.domain.notification.service;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.notification.dto.NotificationResponseDto;
import com.rivermh.soratrip.domain.notification.entity.Notification;
import com.rivermh.soratrip.domain.notification.entity.NotificationType;
import com.rivermh.soratrip.domain.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final MemberRepository memberRepository;
	private final MessageSource messageSource;

	// 알림 생성 (본인의 행동에 대해서는 알림을 만들지 않음)
	@Transactional
	public void notify(Member recipient, Member actor, NotificationType type, Long targetId, String contextTitle) {
		if (recipient.getId().equals(actor.getId())) {
			return;
		}

		notificationRepository.save(Notification.builder()
				.recipient(recipient)
				.actor(actor)
				.type(type)
				.targetId(targetId)
				.contextTitle(contextTitle)
				.build());
	}

	public List<NotificationResponseDto> getRecent(String email) {
		Member member = getMember(email);
		Locale locale = LocaleContextHolder.getLocale();
		return notificationRepository.findTop30ByRecipientOrderByCreatedAtDesc(member).stream()
				.map(n -> toDto(n, locale))
				.collect(Collectors.toList());
	}

	public long getUnreadCount(String email) {
		return notificationRepository.countByRecipientAndReadFalse(getMember(email));
	}

	@Transactional
	public void markAllAsRead(String email) {
		Member member = getMember(email);
		notificationRepository.findByRecipientAndReadFalse(member)
				.forEach(Notification::markAsRead);
	}

	private Member getMember(String email) {
		return memberRepository.findByEmail(email)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
	}

	private NotificationResponseDto toDto(Notification n, Locale locale) {
		String messageKey = "notification.type." + n.getType().name().toLowerCase();
		String message = messageSource.getMessage(messageKey,
				new Object[]{n.getActor().getNickname(), n.getContextTitle()}, locale);

		String link = switch (n.getType()) {
			case POST_LIKE, POST_COMMENT -> "/posts/" + n.getTargetId();
			case SCHEDULE_LIKE -> "/schedules/" + n.getTargetId();
		};

		return new NotificationResponseDto(n.getId(), message, link, n.isRead(), n.getCreatedAt());
	}
}
