package com.rivermh.soratrip.domain.like.repository;

import com.rivermh.soratrip.domain.like.entity.ScheduleLike;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScheduleLikeRepository extends JpaRepository<ScheduleLike, Long> {
    Optional<ScheduleLike> findByMemberAndTravelSchedule(Member member, TravelSchedule travelSchedule);
    boolean existsByMemberAndTravelSchedule(Member member, TravelSchedule travelSchedule);
    
    // 💡 추가: 특정 일정의 총 좋아요 수 카운트
    int countByTravelSchedule(TravelSchedule travelSchedule);
}