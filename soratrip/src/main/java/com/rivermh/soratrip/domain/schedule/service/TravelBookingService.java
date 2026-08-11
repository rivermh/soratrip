package com.rivermh.soratrip.domain.schedule.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.schedule.dto.TravelBookingResponseDto;
import com.rivermh.soratrip.domain.schedule.entity.BookingType;
import com.rivermh.soratrip.domain.schedule.entity.TravelBooking;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TravelBookingRepository;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelBookingService {

    private final TravelBookingRepository travelBookingRepository;
    private final TravelScheduleRepository travelScheduleRepository;

    public List<TravelBookingResponseDto> getBookings(Long scheduleId) {
        return travelBookingRepository.findByTravelScheduleIdOrderByIdAsc(scheduleId).stream()
                .map(TravelBookingResponseDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public Long addBooking(Long scheduleId, String email, BookingType bookingType, String carrierName,
                            String confirmationNumber, LocalDateTime departureTime, LocalDateTime arrivalTime,
                            String memo) {
        TravelSchedule schedule = travelScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다. id=" + scheduleId));
        if (!schedule.isOwnedBy(email)) {
            throw new IllegalStateException("본인의 일정에만 예약 정보를 추가할 수 있습니다.");
        }
        if (carrierName == null || carrierName.isBlank()) {
            throw new IllegalArgumentException("편명/업체명을 입력해주세요.");
        }

        TravelBooking booking = TravelBooking.builder()
                .travelSchedule(schedule)
                .bookingType(bookingType)
                .carrierName(carrierName.trim())
                .confirmationNumber(confirmationNumber != null ? confirmationNumber.trim() : null)
                .departureTime(departureTime)
                .arrivalTime(arrivalTime)
                .memo(memo != null ? memo.trim() : null)
                .build();
        return travelBookingRepository.save(booking).getId();
    }

    @Transactional
    public void deleteBooking(Long bookingId, String email) {
        TravelBooking booking = travelBookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("예약 정보를 찾을 수 없습니다. id=" + bookingId));
        if (!booking.getTravelSchedule().isOwnedBy(email)) {
            throw new IllegalStateException("본인의 예약 정보만 삭제할 수 있습니다.");
        }
        travelBookingRepository.delete(booking);
    }
}
