package com.rivermh.soratrip.domain.schedule.entity;

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

// 여행 준비물 체크리스트 항목 (일정 하나당 공유되는 단일 리스트)
@Entity
@Table(name = "checklist_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_schedule_id", nullable = false)
    private TravelSchedule travelSchedule;

    private String content;

    private boolean checked;

    @Builder
    public ChecklistItem(TravelSchedule travelSchedule, String content) {
        this.travelSchedule = travelSchedule;
        this.content = content;
        this.checked = false;
    }

    public void toggle() {
        this.checked = !this.checked;
    }
}
