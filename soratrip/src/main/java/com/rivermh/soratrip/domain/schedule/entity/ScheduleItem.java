package com.rivermh.soratrip.domain.schedule.entity;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ScheduleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_day_id", nullable = false)
    private ScheduleDay scheduleDay;

    @Column(nullable = false)
    private String placeName; // 예: "도쿄 타워"
    
    private String placeAddress; // 예: "4 Chome-2-8 Shibakoen, Minato City, Tokyo"
    private Double latitude;     // 위도
    private Double longitude;    // 경도

    private LocalTime visitTime; // 방문 예정 시간
    private Integer visitOrder;  // 해당 일자 내 방문 순서 (1, 2, 3...)
    
    @Column(length = 500)
    private String memo;         // 메모 (예: "티켓 현장 발급, 30분 전 도착하기")

    @Column(length = 300)
    private String recommendReason; // AI가 이 장소를 추천한 이유 (AI 생성 시에만 채워짐, 사용자 수동 추가 시 null)

    public void setVisitOrder(Integer visitOrder) {
        this.visitOrder = visitOrder;
    }
}