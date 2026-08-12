package com.rivermh.soratrip.domain.schedule.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.rivermh.soratrip.domain.expense.entity.Expense;
import com.rivermh.soratrip.domain.expense.entity.ExpenseCategory;
import com.rivermh.soratrip.domain.photo.entity.Photo;
import com.rivermh.soratrip.domain.review.entity.Review;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;

import lombok.Getter;

// 여행 일지 화면용 집계 뷰모델 (일정 + 일자별 지출/사진/후기 + 전체 요약)
@Getter
public class ScheduleJournalDto {

    private final TravelSchedule schedule;
    private final Map<Long, List<Expense>> expensesByDay;
    private final Map<Long, List<Photo>> photosByDay;
    private final Map<Long, Review> reviewByDay;
    private final BigDecimal total;
    private final BigDecimal totalJpy;
    private final Map<ExpenseCategory, BigDecimal> byCategory;

    public ScheduleJournalDto(TravelSchedule schedule, Map<Long, List<Expense>> expensesByDay,
            Map<Long, List<Photo>> photosByDay, Map<Long, Review> reviewByDay,
            BigDecimal total, BigDecimal totalJpy, Map<ExpenseCategory, BigDecimal> byCategory) {
        this.schedule = schedule;
        this.expensesByDay = expensesByDay;
        this.photosByDay = photosByDay;
        this.reviewByDay = reviewByDay;
        this.total = total;
        this.totalJpy = totalJpy;
        this.byCategory = byCategory;
    }
}
