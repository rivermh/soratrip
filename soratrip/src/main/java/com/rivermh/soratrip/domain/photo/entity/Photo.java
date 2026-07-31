package com.rivermh.soratrip.domain.photo.entity;

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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "photos")
public class Photo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_schedule_id", nullable = false)
    private TravelSchedule travelSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_day_id", nullable = false)
    private ScheduleDay scheduleDay;

    @Column(nullable = false)
    private String imageUrl; // 예: "/uploads/2026/07/29/<uuid>.jpg"

    private String originalFileName;

    @Column(length = 300)
    private String caption;

    @Builder
    public Photo(TravelSchedule travelSchedule, ScheduleDay scheduleDay, String imageUrl,
            String originalFileName, String caption) {
        this.travelSchedule = travelSchedule;
        this.scheduleDay = scheduleDay;
        this.imageUrl = imageUrl;
        this.originalFileName = originalFileName;
        this.caption = caption;
    }
}
