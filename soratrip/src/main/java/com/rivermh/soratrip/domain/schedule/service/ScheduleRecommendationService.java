package com.rivermh.soratrip.domain.schedule.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.review.entity.Review;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleDay;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleRecommendationService {

    private final TravelScheduleRepository travelScheduleRepository;
    private final MemberRepository memberRepository;

    /**
     * 이동 편의 가중치 및 유저 취향 매칭 기반 일정 추천
     */
    public List<TravelSchedule> recommendFor(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        Set<ScheduleTag> preferred = member.getPreferredTags();

        // N+1 문제 방지를 위해 tags가 Fetch Join된 공개 일정 목록 조회
        List<TravelSchedule> publicSchedules = travelScheduleRepository.findAllPublicWithTags();

        return publicSchedules.stream()
                // 본인 작성 일정 제외
                .filter(ts -> !ts.getMember().getId().equals(member.getId()))
                // 추천 점수(Score) 계산 후 내림차순 정렬 (동점 시 최신순)
                .sorted(Comparator.comparingInt((TravelSchedule ts) -> calculateRecommendationScore(ts, preferred))
                        .reversed()
                        .thenComparing(TravelSchedule::getId, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /**
     * 일정 추천 점수(Recommendation Score) 산출 알고리즘
     */
    private int calculateRecommendationScore(TravelSchedule schedule, Set<ScheduleTag> userPreferredTags) {
        int score = 0;

        Set<ScheduleTag> scheduleTags = schedule.getTags();

        // 1. 사용자 선호 태그 매칭 점수 (+10점 per tag)
        if (userPreferredTags != null && !userPreferredTags.isEmpty()) {
            long matchedCount = scheduleTags.stream()
                    .filter(userPreferredTags::contains)
                    .count();
            score += (int) (matchedCount * 10);
        }

        // 2. 캐리어 / 이동 편의 핵심 태그 가중치 (+15점 per tag)
        for (ScheduleTag tag : scheduleTags) {
            if (isMobilityFriendlyTag(tag)) {
                score += 15;
            }
        }

        // 3. 실제 일자별 리뷰에 기록된 이동 편의 검증 점수 (+5점 per verified feature)
        if (schedule.getDays() != null) {
            for (ScheduleDay day : schedule.getDays()) {
                Review review = day.getReview();
                if (review != null) {
                    // Review 엔티티의 Boolean 필드 Getter 사용 (Null Safe)
                    if (Boolean.TRUE.equals(review.getHasElevator())) score += 5;
                    if (Boolean.TRUE.equals(review.getIsFlatPath())) score += 5;
                    if (Boolean.TRUE.equals(review.getHasLuggageStorage())) score += 5;
                }
            }
        }

        return score;
    }

    /**
     * 이동 편의 / 캐리어 친화 관련 핵심 태그 판별
     */
    private boolean isMobilityFriendlyTag(ScheduleTag tag) {
        if (tag == null) return false;
        String tagName = tag.name();
        
        return tagName.contains("LUGGAGE") ||     // 대형 캐리어
               tagName.contains("ELEVATOR") ||    // 엘리베이터 우선
               tagName.contains("FLAT") ||        // 평지/계단없음
               tagName.contains("ACCESSIBLE") ||  // 배리어프리
               tagName.contains("STROLLER");      // 유모차 수월
    }
}