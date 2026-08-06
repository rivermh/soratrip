package com.rivermh.soratrip.domain.schedule.repository;

import com.rivermh.soratrip.domain.schedule.entity.ScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {
}