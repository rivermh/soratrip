package com.rivermh.soratrip.domain.notification.entity;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 알림을 받는 사람
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recipient_id", nullable = false)
	private Member recipient;

	// 알림을 발생시킨 사람 (좋아요/댓글을 남긴 사람)
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "actor_id", nullable = false)
	private Member actor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	// 게시글/일정 등 알림 대상의 id (링크 생성에 사용)
	@Column(nullable = false)
	private Long targetId;

	// 알림 생성 시점의 게시글/일정 제목 스냅샷 (이후 제목이 바뀌어도 알림 문구는 유지됨)
	@Column(nullable = false)
	private String contextTitle;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Builder
	public Notification(Member recipient, Member actor, NotificationType type, Long targetId, String contextTitle) {
		this.recipient = recipient;
		this.actor = actor;
		this.type = type;
		this.targetId = targetId;
		this.contextTitle = contextTitle;
		this.read = false;
	}

	public void markAsRead() {
		this.read = true;
	}
}
