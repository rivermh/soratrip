package com.rivermh.soratrip.domain.settlement.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rivermh.soratrip.domain.settlement.entity.SettlementCompletion;

public interface SettlementCompletionRepository extends JpaRepository<SettlementCompletion, Long> {

    Optional<SettlementCompletion> findByFromParticipant_IdAndToParticipant_Id(Long fromParticipantId, Long toParticipantId);

    List<SettlementCompletion> findByFromParticipant_TravelSchedule_Id(Long scheduleId);

    void deleteByFromParticipant_IdOrToParticipant_Id(Long fromParticipantId, Long toParticipantId);

    void deleteByFromParticipant_IdInOrToParticipant_IdIn(List<Long> fromParticipantIds, List<Long> toParticipantIds);
}
