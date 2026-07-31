package com.rivermh.soratrip.domain.schedule.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;

public interface TravelScheduleRepository extends JpaRepository<TravelSchedule, Long> {

    // 특정 회원의 일정 목록 조회 (최신순)
    List<TravelSchedule> findByMemberIdOrderByIdDesc(Long memberId);

    // Fetch Join을 통해 ScheduleDay와 ScheduleItem을 한 번에 조회 (N+1 문제 방지)
    @Query("select distinct ts from TravelSchedule ts " +
    	       "left join fetch ts.days d " +
    	       "where ts.id = :id")
    	Optional<TravelSchedule> findByIdWithDetails(@Param("id") Long id);

    // 공개 일정 둘러보기 (지역/태그 필터 + 페이징)
    @Query(value = "SELECT DISTINCT ts FROM TravelSchedule ts LEFT JOIN ts.tags t " +
           "WHERE ts.isPublic = true " +
           "AND (:region IS NULL OR ts.region = :region) " +
           "AND (:tag IS NULL OR t = :tag)",
           countQuery = "SELECT COUNT(DISTINCT ts) FROM TravelSchedule ts LEFT JOIN ts.tags t " +
           "WHERE ts.isPublic = true " +
           "AND (:region IS NULL OR ts.region = :region) " +
           "AND (:tag IS NULL OR t = :tag)")
    Page<TravelSchedule> searchPublic(@Param("region") Region region, @Param("tag") ScheduleTag tag, Pageable pageable);

    // 추천 후보군: 공개 일정 + 태그 Fetch Join (N+1 방지)
    @Query("SELECT DISTINCT ts FROM TravelSchedule ts LEFT JOIN FETCH ts.tags WHERE ts.isPublic = true")
    List<TravelSchedule> findAllPublicWithTags();

    // 내가 완료한 여행 목록 (마이페이지, 공개 여부 무관)
    List<TravelSchedule> findByMemberIdAndEndDateBeforeOrderByEndDateDesc(Long memberId, LocalDate today);

    // 특정 회원의 공개된 완료 여행 목록 (포트폴리오)
    List<TravelSchedule> findByMemberIdAndIsPublicTrueAndEndDateBeforeOrderByEndDateDesc(Long memberId, LocalDate today);
}