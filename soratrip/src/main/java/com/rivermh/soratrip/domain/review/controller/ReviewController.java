package com.rivermh.soratrip.domain.review.controller;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.rivermh.soratrip.domain.review.dto.ReviewUpsertDto;
import com.rivermh.soratrip.domain.review.entity.Review;
import com.rivermh.soratrip.domain.review.service.ReviewService;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.service.TravelScheduleService;
import com.rivermh.soratrip.global.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/schedules/{scheduleId}")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final TravelScheduleService travelScheduleService;

    // 하루 후기 작성/수정
    @PostMapping("/days/{dayId}/review")
    public String saveReview(@PathVariable("scheduleId") Long scheduleId,
                             @PathVariable("dayId") Long dayId,
                             @ModelAttribute ReviewUpsertDto dto,
                             Principal principal) {
        reviewService.saveReview(scheduleId, dayId, dto, principal.getName());
        return "redirect:/schedules/" + scheduleId + "/reviews";
    }

    // 일정 전체 후기 (일자별로 묶어서 표시, 소유자만 작성/수정 가능)
    @GetMapping("/reviews")
    public String scheduleReviews(@PathVariable("scheduleId") Long scheduleId,
                                  @AuthenticationPrincipal Object principal,
                                  Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(scheduleId);

        String email = principal != null ? SecurityUtils.extractEmail(principal) : null;
        boolean owner = schedule.isOwnedBy(email);

        if (!schedule.isPublic() && !owner) {
            return "redirect:/schedules";
        }

        List<Review> reviews = reviewService.getReviewsForSchedule(scheduleId);

        model.addAttribute("schedule", schedule);
        model.addAttribute("owner", owner);
        model.addAttribute("reviewByDay", reviews.stream()
                .collect(Collectors.toMap(
                        r -> r.getScheduleDay().getId(),
                        r -> r,
                        (existing, replacement) -> existing)));
        return "review/list";
    }
}
