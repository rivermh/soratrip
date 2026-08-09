package com.rivermh.soratrip.domain.settlement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.expense.entity.Expense;
import com.rivermh.soratrip.domain.expense.repository.ExpenseRepository;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;
import com.rivermh.soratrip.domain.settlement.dto.ParticipantBalanceDto;
import com.rivermh.soratrip.domain.settlement.dto.SettlementResultDto;
import com.rivermh.soratrip.domain.settlement.dto.SettlementTransactionDto;
import com.rivermh.soratrip.domain.settlement.entity.TripParticipant;
import com.rivermh.soratrip.domain.settlement.repository.TripParticipantRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettlementService {

    private final TripParticipantRepository tripParticipantRepository;
    private final ExpenseRepository expenseRepository;
    private final TravelScheduleRepository travelScheduleRepository;

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
            BigDecimal share = amount.divide(BigDecimal.valueOf(e.getSharedWith().size()), 0, RoundingMode.HALF_UP);

            netAmount.merge(e.getPaidBy().getId(), amount, BigDecimal::add);
            for (TripParticipant sp : e.getSharedWith()) {
                netAmount.merge(sp.getId(), share.negate(), BigDecimal::add);
            }
        }

        List<ParticipantBalanceDto> balances = new ArrayList<>();
        for (TripParticipant p : participants) {
            balances.add(new ParticipantBalanceDto(p.getId(), p.getName(), netAmount.get(p.getId())));
        }

        return new SettlementResultDto(balances, simplifyDebts(balances));
    }

    // 채권자/채무자를 큰 금액순으로 매칭해서 총 송금 횟수를 최소화
    private List<SettlementTransactionDto> simplifyDebts(List<ParticipantBalanceDto> balances) {
        List<MutableBalance> creditors = new ArrayList<>();
        List<MutableBalance> debtors = new ArrayList<>();
        for (ParticipantBalanceDto b : balances) {
            int cmp = b.getNetAmount().compareTo(BigDecimal.ZERO);
            if (cmp > 0) {
                creditors.add(new MutableBalance(b.getName(), b.getNetAmount()));
            } else if (cmp < 0) {
                debtors.add(new MutableBalance(b.getName(), b.getNetAmount().negate()));
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
                transactions.add(new SettlementTransactionDto(debtor.name, creditor.name, settled));
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
        private final String name;
        private BigDecimal amount;

        private MutableBalance(String name, BigDecimal amount) {
            this.name = name;
            this.amount = amount;
        }
    }
}
