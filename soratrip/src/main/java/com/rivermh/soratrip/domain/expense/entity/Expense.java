package com.rivermh.soratrip.domain.expense.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "expenses")
public class Expense extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_schedule_id", nullable = false)
    private TravelSchedule travelSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_day_id", nullable = false)
    private ScheduleDay scheduleDay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency; // JPY 또는 KRW

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount; // 입력한 원본 금액 (예: 1000엔 또는 10000원)

    @Column(precision = 10, scale = 4)
    private BigDecimal exchangeRate; // 적용 환율 (예: 100엔당 900원 기준시 9.0000)

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountKrw; // 최종 원화 환산 금액 (통계/합계용)

    @Column(length = 500)
    private String memo;

    @Builder
    public Expense(TravelSchedule travelSchedule, ScheduleDay scheduleDay, ExpenseCategory category,
                   Currency currency, BigDecimal amount, BigDecimal exchangeRate, String memo) {
        this.travelSchedule = travelSchedule;
        this.scheduleDay = scheduleDay;
        this.category = category;
        this.currency = currency != null ? currency : Currency.JPY;
        this.amount = amount;
        this.exchangeRate = exchangeRate;
        this.memo = memo;
        this.amountKrw = calculateKrwAmount(this.currency, amount, exchangeRate);
    }

    // 환율 환산 도우미 메서드 (JPY -> KRW 계산)
    private BigDecimal calculateKrwAmount(Currency currency, BigDecimal amount, BigDecimal exchangeRate) {
        if (currency == Currency.KRW || exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) == 0) {
            return amount;
        }
        // JPY의 경우 (금액 * 환율 / 100) 또는 (금액 * 1엔당 원화가치)
        // exchangeRate가 100엔당 원화 기준(예: 900.5)일 경우: amount * exchangeRate / 100
        return amount.multiply(exchangeRate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
    }
}