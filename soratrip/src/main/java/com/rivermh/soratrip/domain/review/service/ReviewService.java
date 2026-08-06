package com.rivermh.soratrip.domain.review.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.review.dto.ReviewUpsertDto;
import com.rivermh.soratrip.domain.review.entity.Review;
import com.rivermh.soratrip.domain.review.repository.ReviewRepository;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.repository.ScheduleDayRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ScheduleDayRepository scheduleDayRepository;

    // 하루 후기 작성/수정 (upsert)
    @Transactional
    public void saveReview(Long scheduleId, Long dayId, ReviewUpsertDto dto, String email) {
        ScheduleDay day = scheduleDayRepository.findById(dayId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일자를 찾을 수 없습니다. id=" + dayId));

        if (!day.getTravelSchedule().getId().equals(scheduleId)) {
            throw new IllegalArgumentException("일정과 일자 정보가 일치하지 않습니다.");
        }
        if (!day.getTravelSchedule().getMember().getEmail().equals(email)) {
            throw new IllegalStateException("본인의 일정에만 후기를 작성할 수 있습니다.");
        }

        reviewRepository.findByScheduleDayId(dayId)
                .ifPresentOrElse(
                        review -> review.updateReview(
                                dto.getContent(),
                                dto.getRating(),
                                dto.getHasElevator(),
                                dto.getIsFlatPath(),
                                dto.getHasLuggageStorage()
                        ),
                        () -> reviewRepository.save(Review.builder()
                                .travelSchedule(day.getTravelSchedule())
                                .scheduleDay(day)
                                .content(dto.getContent())
                                .rating(dto.getRating())
                                .hasElevator(dto.getHasElevator())
                                .isFlatPath(dto.getIsFlatPath())
                                .hasLuggageStorage(dto.getHasLuggageStorage())
                                .build()));
    }

    // 일정 전체 후기 목록
    public List<Review> getReviewsForSchedule(Long scheduleId) {
        return reviewRepository.findByTravelScheduleIdWithDay(scheduleId);
    }
}