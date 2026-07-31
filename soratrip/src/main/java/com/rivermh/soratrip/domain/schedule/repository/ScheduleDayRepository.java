package com.rivermh.soratrip.domain.schedule.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;

public interface ScheduleDayRepository extends JpaRepository<ScheduleDay, Long> {
}
