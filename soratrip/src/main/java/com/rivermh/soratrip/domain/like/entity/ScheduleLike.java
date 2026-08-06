package com.rivermh.soratrip.domain.like.entity;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "schedule_likes", uniqueConstraints = {
    @UniqueConstraint(name = "uk_schedule_like_member_schedule", columnNames = {"member_id", "travel_schedule_id"})
})
public class ScheduleLike extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_schedule_id", nullable = false)
    private TravelSchedule travelSchedule;

    @Builder
    public ScheduleLike(Member member, TravelSchedule travelSchedule) {
        this.member = member;
        this.travelSchedule = travelSchedule;
    }
}