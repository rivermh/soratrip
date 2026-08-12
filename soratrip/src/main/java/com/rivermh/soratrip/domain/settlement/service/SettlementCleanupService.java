package com.rivermh.soratrip.domain.settlement.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.expense.entity.Expense;
import com.rivermh.soratrip.domain.expense.repository.ExpenseRepository;
import com.rivermh.soratrip.domain.settlement.entity.TripParticipant;
import com.rivermh.soratrip.domain.settlement.repository.SettlementCompletionRepository;
import com.rivermh.soratrip.domain.settlement.repository.TripParticipantRepository;

import lombok.RequiredArgsConstructor;

/**
 * 정산 참여자(TripParticipant) 제거 시 함께 정리해야 하는 FK 참조(지출의 낸사람/나눈사람,
 * 정산완료 기록)를 정리하는 공용 로직. 회원 탈퇴, 일정 삭제, 참여자 개별 삭제 세 곳에서
 * 동일한 정리 순서(참조 끊기 -> 정산완료 기록 삭제 -> 참여자 삭제)가 필요해서 여기로 모았다.
 */
@Service
@RequiredArgsConstructor
public class SettlementCleanupService {

    private final ExpenseRepository expenseRepository;
    private final TripParticipantRepository tripParticipantRepository;
    private final SettlementCompletionRepository settlementCompletionRepository;

    // 일정의 참여자 전원을 제거한다 (회원 탈퇴, 일정 전체 삭제 등 참여자를 통째로 정리할 때 사용)
    @Transactional
    public void detachAllParticipants(Long scheduleId) {
        List<Expense> expenses = expenseRepository.findByTravelScheduleIdWithDay(scheduleId);
        for (Expense expense : expenses) {
            expense.clearPaidBy();
            expense.getSharedWith().clear();
        }

        List<TripParticipant> participants = tripParticipantRepository.findByTravelScheduleIdOrderByIdAsc(scheduleId);
        if (!participants.isEmpty()) {
            List<Long> participantIds = participants.stream().map(TripParticipant::getId).toList();
            settlementCompletionRepository.deleteByFromParticipant_IdInOrToParticipant_IdIn(participantIds, participantIds);
            tripParticipantRepository.deleteAll(participants);
        }
    }

    // 참여자 한 명만 제거한다 (나머지 참여자와 그들의 지출 분담은 그대로 유지)
    @Transactional
    public void detachParticipant(TripParticipant participant) {
        List<Expense> expenses = expenseRepository.findByTravelScheduleIdWithDay(participant.getTravelSchedule().getId());
        for (Expense expense : expenses) {
            if (expense.getPaidBy() != null && expense.getPaidBy().getId().equals(participant.getId())) {
                expense.clearPaidBy();
            }
            expense.getSharedWith().remove(participant);
        }

        settlementCompletionRepository.deleteByFromParticipant_IdOrToParticipant_Id(participant.getId(), participant.getId());
        tripParticipantRepository.delete(participant);
    }
}
