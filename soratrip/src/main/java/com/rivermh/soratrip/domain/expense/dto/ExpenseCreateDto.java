package com.rivermh.soratrip.domain.expense.dto;

import java.math.BigDecimal;

import com.rivermh.soratrip.domain.expense.entity.ExpenseCategory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ExpenseCreateDto {
    private ExpenseCategory category;
    private BigDecimal amount;
    private String memo;
}
