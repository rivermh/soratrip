package com.rivermh.soratrip.domain.review.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rivermh.soratrip.domain.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByScheduleDayId(Long scheduleDayId);

    // 일정 전체 후기 목록 (일자 Fetch Join)
    @Query("SELECT r FROM Review r JOIN FETCH r.scheduleDay d WHERE r.travelSchedule.id = :scheduleId ORDER BY d.dayNumber ASC")
    List<Review> findByTravelScheduleIdWithDay(@Param("scheduleId") Long scheduleId);
}
