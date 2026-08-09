package com.rivermh.soratrip.domain.notification.repository;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	List<Notification> findTop30ByRecipientOrderByCreatedAtDesc(Member recipient);

	long countByRecipientAndReadFalse(Member recipient);

	List<Notification> findByRecipientAndReadFalse(Member recipient);
}
