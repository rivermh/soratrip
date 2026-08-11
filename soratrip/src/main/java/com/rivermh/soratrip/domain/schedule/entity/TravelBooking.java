package com.rivermh.soratrip.domain.schedule.entity;

import java.time.LocalDateTime;

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

// 항공권/기차표 등 예약 확인번호 보관 (일정 하나당 여러 건, 소유자만 추가/삭제)
@Entity
@Table(name = "travel_bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TravelBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_schedule_id", nullable = false)
    private TravelSchedule travelSchedule;

    @Enumerated(EnumType.STRING)
    private BookingType bookingType;

    private String carrierName; // 예: "대한항공 KE001", "신칸센 노조미 1호"

    private String confirmationNumber; // 예약/발권 확인번호

    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;

    private String memo;

    @Builder
    public TravelBooking(TravelSchedule travelSchedule, BookingType bookingType, String carrierName,
                          String confirmationNumber, LocalDateTime departureTime, LocalDateTime arrivalTime,
                          String memo) {
        this.travelSchedule = travelSchedule;
        this.bookingType = bookingType;
        this.carrierName = carrierName;
        this.confirmationNumber = confirmationNumber;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.memo = memo;
    }
}
