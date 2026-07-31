package com.rivermh.soratrip.domain.photo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rivermh.soratrip.domain.photo.entity.Photo;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    List<Photo> findByScheduleDayIdOrderByIdAsc(Long scheduleDayId);

    // 일정 전체 사진 목록 (일자 Fetch Join, N+1 방지)
    @Query("SELECT p FROM Photo p JOIN FETCH p.scheduleDay d WHERE p.travelSchedule.id = :scheduleId " +
           "ORDER BY d.dayNumber ASC, p.id ASC")
    List<Photo> findByTravelScheduleIdWithDay(@Param("scheduleId") Long scheduleId);

    // 포트폴리오 대표 사진 (최초 업로드 사진)
    Optional<Photo> findFirstByTravelScheduleIdOrderByIdAsc(Long travelScheduleId);

    // 일정 삭제 전 디스크 파일 정리를 위한 전체 목록 조회
    List<Photo> findByTravelScheduleId(Long travelScheduleId);
}
