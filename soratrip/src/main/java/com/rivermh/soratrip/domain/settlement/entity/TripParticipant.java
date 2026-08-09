package com.rivermh.soratrip.domain.settlement.entity;

import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;

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

// 정산(더치페이)에 참여하는 사람. SoraTrip 계정이 아니어도 되는 이름표 개념.
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "trip_participants")
public class TripParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_schedule_id", nullable = false)
    private TravelSchedule travelSchedule;

    private String name;

    @Builder
    public TripParticipant(TravelSchedule travelSchedule, String name) {
        this.travelSchedule = travelSchedule;
        this.name = name;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public boolean belongsTo(Long scheduleId) {
        return travelSchedule != null && travelSchedule.getId().equals(scheduleId);
    }
}
