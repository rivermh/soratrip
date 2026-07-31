package com.rivermh.soratrip.domain.expense.repository;

import java.math.BigDecimal;

import com.rivermh.soratrip.domain.expense.entity.ExpenseCategory;

// 카테고리별 지출 합계 프로젝션
public interface ExpenseCategoryTotal {
    ExpenseCategory getCategory();
    BigDecimal getTotal();
}
