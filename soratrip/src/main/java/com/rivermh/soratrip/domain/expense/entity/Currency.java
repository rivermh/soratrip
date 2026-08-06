package com.rivermh.soratrip.domain.expense.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Currency {
    KRW("원화 (₩)", "₩"),
    JPY("엔화 (¥)", "¥");

    private final String displayName;
    private final String symbol;
}