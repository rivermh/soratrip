package com.rivermh.soratrip.domain.review.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.rivermh.soratrip.domain.review.dto.ReviewUpsertDto;
import com.rivermh.soratrip.domain.review.service.ReviewService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/schedules/{scheduleId}/days/{dayId}/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // 하루 후기 작성/수정
    @PostMapping
    public String saveReview(@PathVariable("scheduleId") Long scheduleId,
                             @PathVariable("dayId") Long dayId,
                             @ModelAttribute ReviewUpsertDto dto,
                             Principal principal) {
        reviewService.saveReview(scheduleId, dayId, dto, principal.getName());
        return "redirect:/schedules/" + scheduleId;
    }
}
