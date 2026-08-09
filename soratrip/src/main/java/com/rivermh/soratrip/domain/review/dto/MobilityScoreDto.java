package com.rivermh.soratrip.domain.review.dto;

import java.util.Collection;

import com.rivermh.soratrip.domain.review.entity.Review;

import lombok.Getter;

// 일정 전체 리뷰에 기록된 이동 편의성(엘리베이터/평지/짐보관) 체크를 집계한 점수
@Getter
public class MobilityScoreDto {

    public enum Level { GOOD, FAIR, LOW, NONE }

    private final long reviewCount;
    private final long elevatorCount;
    private final long flatPathCount;
    private final long luggageCount;
    private final int scorePercent;
    private final Level level;

    public MobilityScoreDto(long reviewCount, long elevatorCount, long flatPathCount, long luggageCount) {
        this.reviewCount = reviewCount;
        this.elevatorCount = elevatorCount;
        this.flatPathCount = flatPathCount;
        this.luggageCount = luggageCount;

        if (reviewCount == 0) {
            this.scorePercent = 0;
            this.level = Level.NONE;
        } else {
            this.scorePercent = (int) Math.round(
                    100.0 * (elevatorCount + flatPathCount + luggageCount) / (reviewCount * 3));
            if (this.scorePercent >= 70) {
                this.level = Level.GOOD;
            } else if (this.scorePercent >= 40) {
                this.level = Level.FAIR;
            } else {
                this.level = Level.LOW;
            }
        }
    }

    public static MobilityScoreDto empty() {
        return new MobilityScoreDto(0, 0, 0, 0);
    }

    // 이미 조회된(Fetch Join 등) 리뷰 컬렉션으로부터 바로 집계 (추가 쿼리 없이 재사용)
    public static MobilityScoreDto fromReviews(Collection<Review> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return empty();
        }
        long elevator = reviews.stream().filter(r -> Boolean.TRUE.equals(r.getHasElevator())).count();
        long flat = reviews.stream().filter(r -> Boolean.TRUE.equals(r.getIsFlatPath())).count();
        long luggage = reviews.stream().filter(r -> Boolean.TRUE.equals(r.getHasLuggageStorage())).count();
        return new MobilityScoreDto(reviews.size(), elevator, flat, luggage);
    }
}
