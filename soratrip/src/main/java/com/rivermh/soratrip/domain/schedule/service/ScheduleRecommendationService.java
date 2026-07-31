package com.rivermh.soratrip.domain.schedule.service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
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

    // 회원의 취향 태그와 겹치는 정도가 높은 공개 일정 순으로 추천 (본인 일정 제외)
    public List<TravelSchedule> recommendFor(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        Set<ScheduleTag> preferred = member.getPreferredTags();

        return travelScheduleRepository.findAllPublicWithTags().stream()
                .filter(ts -> !ts.getMember().getId().equals(member.getId()))
                .sorted(Comparator
                        .comparingInt((TravelSchedule ts) -> (int) ts.getTags().stream().filter(preferred::contains).count())
                        .reversed()
                        .thenComparing(TravelSchedule::getId, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }
}
