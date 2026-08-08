package com.rivermh.soratrip.domain.bookmark.entity;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "schedule_bookmarks",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_member_schedule_bookmark",
            columnNames = {"member_id", "travel_schedule_id"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduleBookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_schedule_id", nullable = false)
    private TravelSchedule travelSchedule;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public ScheduleBookmark(Member member, TravelSchedule travelSchedule) {
        this.member = member;
        this.travelSchedule = travelSchedule;
    }
}