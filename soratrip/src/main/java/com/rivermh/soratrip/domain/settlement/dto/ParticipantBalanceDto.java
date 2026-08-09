package com.rivermh.soratrip.domain.settlement.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ParticipantBalanceDto {
    private final Long participantId;
    private final String name;
    private final BigDecimal netAmount; // 양수: 받을 돈, 음수: 낼 돈, 0: 정산 완료
}
