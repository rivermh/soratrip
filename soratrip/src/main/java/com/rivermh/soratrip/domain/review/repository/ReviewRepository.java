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

    // 여러 일정의 이동 편의성(엘리베이터/평지/짐보관) 체크 집계 (탐색/추천 리스트 카드 배지용, N+1 방지)
    @Query("SELECT r.travelSchedule.id, COUNT(r), " +
           "SUM(CASE WHEN r.hasElevator = true THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN r.isFlatPath = true THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN r.hasLuggageStorage = true THEN 1 ELSE 0 END) " +
           "FROM Review r WHERE r.travelSchedule.id IN :scheduleIds " +
           "GROUP BY r.travelSchedule.id")
    List<Object[]> aggregateMobilityStats(@Param("scheduleIds") List<Long> scheduleIds);
}
