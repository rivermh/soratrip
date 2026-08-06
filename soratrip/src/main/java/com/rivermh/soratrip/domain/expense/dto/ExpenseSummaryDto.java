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

    public ExpenseSummaryDto(BigDecimal totalKrw, BigDecimal totalJpy, Map<ExpenseCategory, BigDecimal> byCategory) {
        this.totalKrw = totalKrw != null ? totalKrw : BigDecimal.ZERO;
        this.totalJpy = totalJpy != null ? totalJpy : BigDecimal.ZERO;
        this.byCategory = byCategory;
    }
}