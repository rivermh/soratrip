package com.rivermh.soratrip.domain.schedule.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rivermh.soratrip.domain.schedule.entity.ChecklistItem;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {
    List<ChecklistItem> findByTravelScheduleIdOrderByIdAsc(Long scheduleId);
}
