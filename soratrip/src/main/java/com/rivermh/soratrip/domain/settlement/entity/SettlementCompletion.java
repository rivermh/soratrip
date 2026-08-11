package com.rivermh.soratrip.domain.settlement.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 정산 결과("누가 누구에게 얼마")에서 실제로 송금이 끝났는지 표시하는 기록.
// (from, to) 참여자 쌍마다 행이 존재하면 완료된 것으로 간주한다 (좋아요/북마크와 동일한 존재=완료 패턴).
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "settlement_completions")
public class SettlementCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_participant_id", nullable = false)
    private TripParticipant fromParticipant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_participant_id", nullable = false)
    private TripParticipant toParticipant;

    @Builder
    public SettlementCompletion(TripParticipant fromParticipant, TripParticipant toParticipant) {
        this.fromParticipant = fromParticipant;
        this.toParticipant = toParticipant;
    }
}
