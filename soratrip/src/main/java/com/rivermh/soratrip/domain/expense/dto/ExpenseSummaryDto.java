package com.rivermh.soratrip.domain.expense.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.rivermh.soratrip.domain.expense.entity.ExpenseCategory;

import lombok.Getter;

@Getter
public class ExpenseSummaryDto {
    private final BigDecimal totalKrw; // 최종 원화 환산 총액 (₩)
    private final BigDecimal totalJpy; // 순수 사용 엔화 총액 (¥)
    private final Map<ExpenseCategory, BigDecimal> byCategory; // 카테고리별 원화 합계
    private final BigDecimal budgetKrw; // 설정된 예산 (원화, 미설정 시 null)
    private final Integer percentUsed; // 예산 대비 사용률(%), 예산 미설정/0이면 null

    public ExpenseSummaryDto(BigDecimal totalKrw, BigDecimal totalJpy, Map<ExpenseCategory, BigDecimal> byCategory,
                              BigDecimal budgetKrw) {
        this.totalKrw = totalKrw != null ? totalKrw : BigDecimal.ZERO;
        this.totalJpy = totalJpy != null ? totalJpy : BigDecimal.ZERO;
        this.byCategory = byCategory;
        this.budgetKrw = budgetKrw;
        this.percentUsed = (budgetKrw != null && budgetKrw.compareTo(BigDecimal.ZERO) > 0)
                ? this.totalKrw.multiply(BigDecimal.valueOf(100))
                        .divide(budgetKrw, 0, java.math.RoundingMode.HALF_UP)
                        .intValue()
                : null;
    }
}