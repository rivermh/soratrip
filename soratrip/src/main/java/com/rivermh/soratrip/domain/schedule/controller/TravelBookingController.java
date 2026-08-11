package com.rivermh.soratrip.domain.schedule.controller;

import java.security.Principal;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.rivermh.soratrip.domain.schedule.entity.BookingType;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.service.TravelBookingService;
import com.rivermh.soratrip.domain.schedule.service.TravelScheduleService;
import com.rivermh.soratrip.global.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

// 항공권/기차표 등 예약 확인번호 보관 (일정 하나당 여러 건, 소유자만 추가/삭제 가능)
@Controller
@RequestMapping("/schedules/{scheduleId}/bookings")
@RequiredArgsConstructor
public class TravelBookingController {

    private final TravelBookingService travelBookingService;
    private final TravelScheduleService travelScheduleService;

    @GetMapping
    public String bookingsPage(@PathVariable("scheduleId") Long scheduleId,
                               @AuthenticationPrincipal Object principal,
                               Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(scheduleId);

        String email = principal != null ? SecurityUtils.extractEmail(principal) : null;
        boolean owner = schedule.isOwnedBy(email);

        if (!schedule.isPublic() && !owner) {
            return "redirect:/schedules";
        }

        model.addAttribute("schedule", schedule);
        model.addAttribute("owner", owner);
        model.addAttribute("bookings", travelBookingService.getBookings(scheduleId));
        model.addAttribute("bookingTypes", BookingType.values());
        return "schedule/bookings";
    }

    @PostMapping
    public String addBooking(@PathVariable("scheduleId") Long scheduleId,
                             @RequestParam("bookingType") BookingType bookingType,
                             @RequestParam("carrierName") String carrierName,
                             @RequestParam(value = "confirmationNumber", required = false) String confirmationNumber,
                             @RequestParam(value = "departureTime", required = false)
                             @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime departureTime,
                             @RequestParam(value = "arrivalTime", required = false)
                             @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime arrivalTime,
                             @RequestParam(value = "memo", required = false) String memo,
                             Principal principal) {
        travelBookingService.addBooking(scheduleId, principal.getName(), bookingType, carrierName,
                confirmationNumber, departureTime, arrivalTime, memo);
        return "redirect:/schedules/" + scheduleId + "/bookings";
    }

    @PostMapping("/{bookingId}/delete")
    public String deleteBooking(@PathVariable("scheduleId") Long scheduleId,
                                @PathVariable("bookingId") Long bookingId,
                                Principal principal) {
        travelBookingService.deleteBooking(bookingId, principal.getName());
        return "redirect:/schedules/" + scheduleId + "/bookings";
    }
}
