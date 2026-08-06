package com.rivermh.soratrip.domain.expense.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rivermh.soratrip.domain.expense.entity.Expense;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByScheduleDayIdOrderByIdAsc(Long scheduleDayId);

    // 일정 전체 지출 내역 (일자 Fetch Join, N+1 방지)
    @Query("SELECT e FROM Expense e JOIN FETCH e.scheduleDay d WHERE e.travelSchedule.id = :scheduleId " +
           "ORDER BY d.dayNumber ASC, e.id ASC")
    List<Expense> findByTravelScheduleIdWithDay(@Param("scheduleId") Long scheduleId);

    // 카테고리별 원화 환산 지출 합계 (e.amount -> e.amountKrw로 수정)
    @Query("SELECT e.category AS category, SUM(e.amountKrw) AS total FROM Expense e " +
           "WHERE e.travelSchedule.id = :scheduleId GROUP BY e.category")
    List<ExpenseCategoryTotal> sumByCategory(@Param("scheduleId") Long scheduleId);

    // 전체 원화 환산 지출 합계 (e.amount -> e.amountKrw로 수정)
    @Query("SELECT COALESCE(SUM(e.amountKrw), 0) FROM Expense e WHERE e.travelSchedule.id = :scheduleId")
    BigDecimal sumTotalByScheduleId(@Param("scheduleId") Long scheduleId);
}