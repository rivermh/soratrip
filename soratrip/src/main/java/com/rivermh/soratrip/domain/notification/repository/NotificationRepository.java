package com.rivermh.soratrip.domain.notification.repository;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	List<Notification> findTop30ByRecipientOrderByCreatedAtDesc(Member recipient);

	long countByRecipientAndReadFalse(Member recipient);

	List<Notification> findByRecipientAndReadFalse(Member recipient);

	// 회원 탈퇴용: 탈퇴 회원이 받은 알림은 더 이상 볼 사람이 없으므로 일괄 삭제
	void deleteByRecipient(Member recipient);

	// 회원 탈퇴용: 탈퇴 회원이 발생시킨(actor) 알림은 받는 사람 화면에 남기되, 행위자만 익명화(null)한다
	@Modifying
	@Query("UPDATE Notification n SET n.actor = null WHERE n.actor = :actor")
	void clearActor(@Param("actor") Member actor);
}
