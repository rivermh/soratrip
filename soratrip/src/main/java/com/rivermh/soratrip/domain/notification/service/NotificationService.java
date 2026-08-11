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

	// 시스템(배치 등)이 발생시키는 알림 생성 (예: 여행 D-day 알림). 특정 행위자가 없으므로 actor 없이 저장
	@Transactional
	public void notifySystem(Member recipient, NotificationType type, Long targetId, String contextTitle) {
		notificationRepository.save(Notification.builder()
				.recipient(recipient)
				.actor(null)
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
		// TRIP_REMINDER 등 시스템 알림은 애초에 actor 없이 {0}=contextTitle 하나만 쓰는 템플릿이고,
		// 좋아요/댓글류는 원래 {0}=actor, {1}=contextTitle 두 자리 템플릿이다. actor가 나중에(회원 탈퇴로
		// actor_id가 null이 되는 경우) 사라지더라도, 좋아요/댓글류는 자리 수가 그대로 유지되어야
		// {1}(제목)이 {0} 자리로 밀려 엉뚱하게 표시되는 걸 막을 수 있다.
		Object[] args = (n.getType() == NotificationType.TRIP_REMINDER)
				? new Object[]{n.getContextTitle()}
				: new Object[]{
						n.getActor() != null
								? n.getActor().getNickname()
								: messageSource.getMessage("notification.unknown_actor", null, locale),
						n.getContextTitle()
				};
		String message = messageSource.getMessage(messageKey, args, locale);

		String link = switch (n.getType()) {
			case POST_LIKE, POST_COMMENT, COMMENT_REPLY, POST_APPLICATION, APPLICATION_ACCEPTED, APPLICATION_REJECTED ->
					"/posts/" + n.getTargetId();
			case SCHEDULE_LIKE, TRIP_REMINDER -> "/schedules/" + n.getTargetId();
		};

		return new NotificationResponseDto(n.getId(), message, link, n.isRead(), n.getCreatedAt());
	}
}
