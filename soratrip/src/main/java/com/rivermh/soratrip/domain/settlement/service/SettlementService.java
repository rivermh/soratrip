package com.rivermh.soratrip.domain.settlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.expense.entity.Expense;
import com.rivermh.soratrip.domain.expense.repository.ExpenseRepository;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;
import com.rivermh.soratrip.domain.settlement.dto.ParticipantBalanceDto;
import com.rivermh.soratrip.domain.settlement.dto.SettlementResultDto;
import com.rivermh.soratrip.domain.settlement.dto.SettlementTransactionDto;
import com.rivermh.soratrip.domain.settlement.entity.SettlementCompletion;
import com.rivermh.soratrip.domain.settlement.entity.TripParticipant;
import com.rivermh.soratrip.domain.settlement.repository.SettlementCompletionRepository;
import com.rivermh.soratrip.domain.settlement.repository.TripParticipantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final TripParticipantRepository tripParticipantRepository;
    private final ExpenseRepository expenseRepository;
    private final TravelScheduleRepository travelScheduleRepository;
    private final SettlementCompletionRepository settlementCompletionRepository;

    // 참여자 추가 (본인 일정에만)
    @Transactional
    public Long addParticipant(Long scheduleId, String name, String email) {
        TravelSchedule schedule = travelScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다. id=" + scheduleId));
        if (!schedule.isOwnedBy(email)) {
            throw new IllegalStateException("본인의 일정에만 참여자를 추가할 수 있습니다.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("이름을 입력해주세요.");
        }

        TripParticipant participant = TripParticipant.builder()
                .travelSchedule(schedule)
                .name(name.trim())
                .build();
        return tripParticipantRepository.save(participant).getId();
    }

    // 참여자 삭제 (참여자가 걸려있는 지출의 낸사람/나눈사람 참조도 함께 정리)
    @Transactional
    public void removeParticipant(Long participantId, String email) {
        TripParticipant participant = tripParticipantRepository.findById(participantId)
                .orElseThrow(() -> new IllegalArgumentException("참여자를 찾을 수 없습니다. id=" + participantId));
        if (!participant.getTravelSchedule().isOwnedBy(email)) {
            throw new IllegalStateException("본인의 일정에서만 참여자를 삭제할 수 있습니다.");
        }

        List<Expense> expenses = expenseRepository.findByTravelScheduleIdWithDay(participant.getTravelSchedule().getId());
        for (Expense expense : expenses) {
            if (expense.getPaidBy() != null && expense.getPaidBy().getId().equals(participantId)) {
                expense.clearPaidBy();
            }
            expense.getSharedWith().remove(participant);
        }

        // 이 참여자가 걸린 송금완료 기록(보내는 쪽/받는 쪽 어느 방향이든)도 함께 정리
        settlementCompletionRepository.deleteByFromParticipant_IdOrToParticipant_Id(participantId, participantId);

        tripParticipantRepository.delete(participant);
    }

    public List<TripParticipant> getParticipants(Long scheduleId) {
        return tripParticipantRepository.findByTravelScheduleIdOrderByIdAsc(scheduleId);
    }

    // 참여자별 순잔액(+받을 돈/-낼 돈) 계산 후 최소 송금 횟수로 정산 내역 생성
    public SettlementResultDto calculateSettlement(Long scheduleId) {
        List<TripParticipant> participants = getParticipants(scheduleId);
        List<Expense> expenses = expenseRepository.findByTravelScheduleIdWithDay(scheduleId);

        Map<Long, BigDecimal> netAmount = new LinkedHashMap<>();
        for (TripParticipant p : participants) {
            netAmount.put(p.getId(), BigDecimal.ZERO);
        }

        for (Expense e : expenses) {
            if (e.getPaidBy() == null || e.getSharedWith() == null || e.getSharedWith().isEmpty()) {
                continue; // 정산 대상으로 지정되지 않은 지출은 건너뜀
            }
            BigDecimal amount = e.getAmountKrw();
            List<TripParticipant> sharedWith = e.getSharedWith().stream()
                    .sorted(Comparator.comparing(TripParticipant::getId))
                    .toList();
            int shareCount = sharedWith.size();

            // 1원 단위로 나눠떨어지지 않으면 기본 분담금은 내림 처리하고, 남는 나머지(원 단위)를
            // 참여자 순서대로 1원씩 분배한다. 이렇게 하면 분담금 합계가 항상 원래 금액과 정확히 일치해서
            // 반올림 오차가 결제자에게 소리 없이 손실되는 일이 없다.
            BigDecimal baseShare = amount.divide(BigDecimal.valueOf(shareCount), 0, RoundingMode.DOWN);
            int remainderWon = amount.subtract(baseShare.multiply(BigDecimal.valueOf(shareCount))).intValue();

            netAmount.merge(e.getPaidBy().getId(), amount, BigDecimal::add);
            for (int i = 0; i < shareCount; i++) {
                BigDecimal share = i < remainderWon ? baseShare.add(BigDecimal.ONE) : baseShare;
                netAmount.merge(sharedWith.get(i).getId(), share.negate(), BigDecimal::add);
            }
        }

        List<ParticipantBalanceDto> balances = new ArrayList<>();
        for (TripParticipant p : participants) {
            balances.add(new ParticipantBalanceDto(p.getId(), p.getName(), netAmount.get(p.getId())));
        }

        Set<String> completedPairs = settlementCompletionRepository.findByFromParticipant_TravelSchedule_Id(scheduleId)
                .stream()
                .map(c -> pairKey(c.getFromParticipant().getId(), c.getToParticipant().getId()))
                .collect(Collectors.toSet());

        return new SettlementResultDto(balances, simplifyDebts(balances, completedPairs));
    }

    // 송금 완료 표시를 토글한다 (기록이 없으면 생성 = 완료 표시, 있으면 삭제 = 완료 취소)
    @Transactional
    public void toggleTransactionCompletion(Long scheduleId, Long fromParticipantId, Long toParticipantId, String email) {
        TravelSchedule schedule = travelScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다. id=" + scheduleId));
        if (!schedule.isOwnedBy(email)) {
            throw new IllegalStateException("본인의 일정에서만 정산 완료 여부를 변경할 수 있습니다.");
        }

        settlementCompletionRepository.findByFromParticipant_IdAndToParticipant_Id(fromParticipantId, toParticipantId)
                .ifPresentOrElse(
                        settlementCompletionRepository::delete,
                        () -> {
                            TripParticipant from = tripParticipantRepository.findById(fromParticipantId)
                                    .orElseThrow(() -> new IllegalArgumentException("참여자를 찾을 수 없습니다. id=" + fromParticipantId));
                            TripParticipant to = tripParticipantRepository.findById(toParticipantId)
                                    .orElseThrow(() -> new IllegalArgumentException("참여자를 찾을 수 없습니다. id=" + toParticipantId));
                            settlementCompletionRepository.save(
                                    SettlementCompletion.builder().fromParticipant(from).toParticipant(to).build());
                        });
    }

    private static String pairKey(Long fromId, Long toId) {
        return fromId + ":" + toId;
    }

    // 채권자/채무자를 큰 금액순으로 매칭해서 총 송금 횟수를 최소화
    private List<SettlementTransactionDto> simplifyDebts(List<ParticipantBalanceDto> balances, Set<String> completedPairs) {
        List<MutableBalance> creditors = new ArrayList<>();
        List<MutableBalance> debtors = new ArrayList<>();
        for (ParticipantBalanceDto b : balances) {
            int cmp = b.getNetAmount().compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                creditors.add(new MutableBalance(b.getParticipantId(), b.getName(), b.getNetAmount()));
            } else if (cmp < 0) {
                debtors.add(new MutableBalance(b.getParticipantId(), b.getName(), b.getNetAmount().negate()));
            }
        }
        creditors.sort((a, b) -> b.amount.compareTo(a.amount));
        debtors.sort((a, b) -> b.amount.compareTo(a.amount));

        List<SettlementTransactionDto> transactions = new ArrayList<>();
        int ci = 0;
        int di = 0;
        while (ci < creditors.size() && di < debtors.size()) {
            MutableBalance creditor = creditors.get(ci);
            MutableBalance debtor = debtors.get(di);
            BigDecimal settled = creditor.amount.min(debtor.amount);

            if (settled.compareTo(BigDecimal.ZERO) > 0) {
                boolean completed = completedPairs.contains(pairKey(debtor.id, creditor.id));
                transactions.add(new SettlementTransactionDto(debtor.id, creditor.id, debtor.name, creditor.name, settled, completed));
            }
            creditor.amount = creditor.amount.subtract(settled);
            debtor.amount = debtor.amount.subtract(settled);

            if (creditor.amount.compareTo(BigDecimal.ZERO) == 0) {
                ci++;
            }
            if (debtor.amount.compareTo(BigDecimal.ZERO) == 0) {
                di++;
            }
        }
        return transactions;
    }

    private static class MutableBalance {
        private final Long id;
        private final String name;
        private BigDecimal amount;

        private MutableBalance(Long id, String name, BigDecimal amount) {
            this.id = id;
            this.name = name;
            this.amount = amount;
        }
    }
}
