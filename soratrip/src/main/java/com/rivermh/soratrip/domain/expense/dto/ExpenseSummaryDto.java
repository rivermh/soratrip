package com.rivermh.soratrip.domain.expense.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.rivermh.soratrip.domain.expense.entity.ExpenseCategory;

import lombok.Getter;

@Getter
public class ExpenseSummaryDto {
    private final BigDecimal total;
    private final Map<ExpenseCategory, BigDecimal> byCategory;

    public ExpenseSummaryDto(BigDecimal total, Map<ExpenseCategory, BigDecimal> byCategory) {
        this.total = total;
        this.byCategory = byCategory;
    }
}
