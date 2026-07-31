package com.rivermh.soratrip.domain.schedule.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

import com.rivermh.soratrip.domain.expense.entity.Expense;
import com.rivermh.soratrip.domain.photo.entity.Photo;
import com.rivermh.soratrip.domain.review.entity.Review;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
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
public class ScheduleDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_schedule_id", nullable = false)
    private TravelSchedule travelSchedule;

    private Integer dayNumber; // 1일차, 2일차 (1, 2, 3...)
    private LocalDate visitDate; // 실제 날짜 (예: 2026-08-01)

    @OneToMany(mappedBy = "scheduleDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("visitOrder ASC")
    @Builder.Default
    private Set<ScheduleItem> items = new LinkedHashSet<>();

    @OneToMany(mappedBy = "scheduleDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Expense> expenses = new LinkedHashSet<>();

    @OneToMany(mappedBy = "scheduleDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<Photo> photos = new LinkedHashSet<>();

    @OneToOne(mappedBy = "scheduleDay", cascade = CascadeType.ALL, orphanRemoval = true)
    private Review review;
    
    public void addItem(ScheduleItem item) {
        this.items.add(item);
    }
}