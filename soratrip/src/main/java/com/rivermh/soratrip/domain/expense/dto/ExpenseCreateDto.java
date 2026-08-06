package com.rivermh.soratrip.domain.expense.dto;

import java.math.BigDecimal;

import com.rivermh.soratrip.domain.expense.entity.Currency;
import com.rivermh.soratrip.domain.expense.entity.ExpenseCategory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ExpenseCreateDto {
    private ExpenseCategory category;
    private Currency currency = Currency.JPY; // 기본값 엔화(JPY)
    private BigDecimal amount;                // 입력한 금액 (예: 1000엔)
    private BigDecimal exchangeRate;          // 100엔당 원화 환율 (예: 900.00) -> null 시 기본 환율 적용
    private String memo;
}