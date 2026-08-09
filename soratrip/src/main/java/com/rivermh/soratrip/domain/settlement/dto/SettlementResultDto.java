package com.rivermh.soratrip.domain.settlement.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SettlementResultDto {
    private final List<ParticipantBalanceDto> balances;
    private final List<SettlementTransactionDto> transactions;
}
