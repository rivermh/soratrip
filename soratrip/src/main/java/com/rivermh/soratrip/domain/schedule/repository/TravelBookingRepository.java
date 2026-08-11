package com.rivermh.soratrip.domain.schedule.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rivermh.soratrip.domain.schedule.entity.TravelBooking;

public interface TravelBookingRepository extends JpaRepository<TravelBooking, Long> {
    List<TravelBooking> findByTravelScheduleIdOrderByIdAsc(Long scheduleId);
}
