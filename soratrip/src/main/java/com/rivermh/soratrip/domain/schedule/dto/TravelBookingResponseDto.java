package com.rivermh.soratrip.domain.schedule.dto;

import java.time.LocalDateTime;

import com.rivermh.soratrip.domain.schedule.entity.BookingType;
import com.rivermh.soratrip.domain.schedule.entity.TravelBooking;

import lombok.Getter;

@Getter
public class TravelBookingResponseDto {

    private final Long id;
    private final BookingType bookingType;
    private final String carrierName;
    private final String confirmationNumber;
    private final LocalDateTime departureTime;
    private final LocalDateTime arrivalTime;
    private final String memo;

    public TravelBookingResponseDto(TravelBooking booking) {
        this.id = booking.getId();
        this.bookingType = booking.getBookingType();
        this.carrierName = booking.getCarrierName();
        this.confirmationNumber = booking.getConfirmationNumber();
        this.departureTime = booking.getDepartureTime();
        this.arrivalTime = booking.getArrivalTime();
        this.memo = booking.getMemo();
    }
}
