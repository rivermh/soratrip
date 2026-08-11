package com.rivermh.soratrip.domain.schedule.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.notification.entity.NotificationType;
import com.rivermh.soratrip.domain.notification.service.NotificationService;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;

import lombok.RequiredArgsConstructor;

// 내일 출발하는 일정의 소유자에게 D-day 알림을 보내는 배치
@Service
@RequiredArgsConstructor
public class ScheduleReminderService {

	private final TravelScheduleRepository travelScheduleRepository;
	private final NotificationService notificationService;

	// 매일 오전 9시(서울 기준)에 실행
	@Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
	@Transactional
	public void sendTripStartReminders() {
		LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);

		for (TravelSchedule schedule : travelScheduleRepository.findByStartDate(tomorrow)) {
			notificationService.notifySystem(
					schedule.getMember(),
					NotificationType.TRIP_REMINDER,
					schedule.getId(),
					schedule.getTitle()
			);
		}
	}
}
