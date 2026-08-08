package com.rivermh.soratrip.domain.bookmark.repository;

import com.rivermh.soratrip.domain.bookmark.entity.ScheduleBookmark;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduleBookmarkRepository extends JpaRepository<ScheduleBookmark, Long> {

    Optional<ScheduleBookmark> findByMemberAndTravelSchedule(Member member, TravelSchedule travelSchedule);

    boolean existsByMemberAndTravelSchedule(Member member, TravelSchedule travelSchedule);

    int countByTravelSchedule(TravelSchedule travelSchedule);

    // 마이페이지용: 내가 북마크한 일정 목록 조회
    List<ScheduleBookmark> findByMemberOrderByCreatedAtDesc(Member member);    
    List<ScheduleBookmark> findByMemberEmailOrderByCreatedAtDesc(String email);
}