package com.rivermh.soratrip.domain.settlement.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SettlementTransactionDto {
    private final Long fromParticipantId;
    private final Long toParticipantId;
    private final String fromName; // 보내는 사람 (빚진 사람)
    private final String toName;   // 받는 사람 (돈을 먼저 낸 사람)
    private final BigDecimal amount;
    private final boolean completed; // 실제로 송금이 끝났다고 표시했는지 여부
}
