package com.rivermh.soratrip.domain.expense.service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.expense.dto.ExpenseCreateDto;
import com.rivermh.soratrip.domain.expense.dto.ExpenseSummaryDto;
import com.rivermh.soratrip.domain.expense.entity.Expense;
import com.rivermh.soratrip.domain.expense.entity.ExpenseCategory;
import com.rivermh.soratrip.domain.expense.repository.ExpenseCategoryTotal;
import com.rivermh.soratrip.domain.expense.repository.ExpenseRepository;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.repository.ScheduleDayRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ScheduleDayRepository scheduleDayRepository;

    // 지출 등록
    @Transactional
    public Long createExpense(Long scheduleId, Long dayId, ExpenseCreateDto dto, String email) {
        ScheduleDay day = scheduleDayRepository.findById(dayId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일자를 찾을 수 없습니다. id=" + dayId));

        if (!day.getTravelSchedule().getId().equals(scheduleId)) {
            throw new IllegalArgumentException("일정과 일자 정보가 일치하지 않습니다.");
        }
        if (!day.getTravelSchedule().getMember().getEmail().equals(email)) {
            throw new IllegalStateException("본인의 일정에만 지출을 등록할 수 있습니다.");
        }

        Expense expense = Expense.builder()
                .travelSchedule(day.getTravelSchedule())
                .scheduleDay(day)
                .category(dto.getCategory())
                .amount(dto.getAmount())
                .memo(dto.getMemo())
                .build();

        return expenseRepository.save(expense).getId();
    }

    // 특정 일자의 지출 목록
    public List<Expense> getExpensesForDay(Long dayId) {
        return expenseRepository.findByScheduleDayIdOrderByIdAsc(dayId);
    }

    // 일정 전체 지출 목록 (일자순)
    public List<Expense> getExpensesForSchedule(Long scheduleId) {
        return expenseRepository.findByTravelScheduleIdWithDay(scheduleId);
    }

    // 일정 전체 지출 요약 (총액 + 카테고리별)
    public ExpenseSummaryDto getScheduleSummary(Long scheduleId) {
        BigDecimal total = expenseRepository.sumTotalByScheduleId(scheduleId);
        Map<ExpenseCategory, BigDecimal> byCategory = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategoryTotal row : expenseRepository.sumByCategory(scheduleId)) {
            byCategory.put(row.getCategory(), row.getTotal());
        }
        return new ExpenseSummaryDto(total, byCategory);
    }

    // 지출 삭제
    @Transactional
    public void deleteExpense(Long expenseId, String email) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new IllegalArgumentException("해당 지출 내역을 찾을 수 없습니다. id=" + expenseId));

        if (!expense.getTravelSchedule().getMember().getEmail().equals(email)) {
            throw new IllegalStateException("본인의 지출 내역만 삭제할 수 있습니다.");
        }
        expenseRepository.delete(expense);
    }
}
