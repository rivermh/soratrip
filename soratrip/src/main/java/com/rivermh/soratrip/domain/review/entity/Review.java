package com.rivermh.soratrip.domain.review.entity;

import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reviews")
public class Review extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_schedule_id", nullable = false)
    private TravelSchedule travelSchedule;

    // 하루에 하나의 후기(일지)만 작성 가능
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_day_id", nullable = false, unique = true)
    private ScheduleDay scheduleDay;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private Integer rating; // 1~5, 선택 입력

    // --- [이동 스트레스 제로 / 접근성 평가 항목 추가] ---
    @Column(nullable = false)
    private Boolean hasElevator; // 엘리베이터/에스컬레이터 원활 여부

    @Column(nullable = false)
    private Boolean isFlatPath; // 계단 없음 / 경사 완만 여부

    @Column(nullable = false)
    private Boolean hasLuggageStorage; // 캐리어/짐 보관 용이 여부

    @Builder
    public Review(TravelSchedule travelSchedule, ScheduleDay scheduleDay, String content, Integer rating,
                  Boolean hasElevator, Boolean isFlatPath, Boolean hasLuggageStorage) {
        this.travelSchedule = travelSchedule;
        this.scheduleDay = scheduleDay;
        this.content = content;
        this.rating = rating;
        this.hasElevator = hasElevator != null ? hasElevator : false;
        this.isFlatPath = isFlatPath != null ? isFlatPath : false;
        this.hasLuggageStorage = hasLuggageStorage != null ? hasLuggageStorage : false;
    }

    // 후기 내용 수정 (같은 날짜 재작성 시 upsert)
    public void updateReview(String content, Integer rating, 
                             Boolean hasElevator, Boolean isFlatPath, Boolean hasLuggageStorage) {
        this.content = content;
        this.rating = rating;
        this.hasElevator = hasElevator != null ? hasElevator : false;
        this.isFlatPath = isFlatPath != null ? isFlatPath : false;
        this.hasLuggageStorage = hasLuggageStorage != null ? hasLuggageStorage : false;
    }
}