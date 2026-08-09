package com.rivermh.soratrip.domain.settlement.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rivermh.soratrip.domain.settlement.entity.TripParticipant;

public interface TripParticipantRepository extends JpaRepository<TripParticipant, Long> {

    List<TripParticipant> findByTravelScheduleIdOrderByIdAsc(Long travelScheduleId);
}
