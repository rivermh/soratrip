package com.rivermh.soratrip.domain.chat.service;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingService {

    private final MemberRepository memberRepository;

    /**
     * 현재 사용자와 관심사가 같은 사람들 찾기
     * @param memberId 현재 사용자 ID
     * @return 매칭된 사용자 목록 (최대 20명)
     */
    public List<MemberMatchInfo> findSimilarUsers(Long memberId) {
        Member currentUser = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 회원의 preferredTags에서 직접 관심사 태그 조회
        Set<String> userTags = getCurrentUserTags(currentUser);

        if (userTags.isEmpty()) {
            log.info("사용자 {}에게 선호 태그가 없어 매칭할 수 없습니다.", memberId);
            return new ArrayList<>();
        }

        List<Member> allMembers = memberRepository.findAll();

        return allMembers.stream()
                .filter(member -> !member.getId().equals(memberId))  // 자신 제외
                .filter(member -> member.getEmail() != null && !member.getEmail().isEmpty())  // 활성 사용자만
                .map(member -> {
                    Set<String> memberTags = getCurrentUserTags(member);
                    double similarity = calculateSimilarity(userTags, memberTags);
                    return new MemberMatchInfo(member, similarity);
                })
                .filter(info -> info.getSimilarity() > 0)  // 공통 관심사 있는 사람만
                .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))  // 유사도순 정렬
                .limit(20)  // 최대 20명
                .collect(Collectors.toList());
    }

    /**
     * 특정 지역의 사람들 찾기
     */
    public List<MemberMatchInfo> findUsersByRegion(Long memberId, String region) {
        Member currentUser = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Set<String> userTags = getCurrentUserTags(currentUser);
        List<Member> allMembers = memberRepository.findAll();

        return allMembers.stream()
                .filter(member -> !member.getId().equals(memberId))
                .filter(member -> hasScheduleInRegion(member, region))
                .map(member -> {
                    Set<String> memberTags = getCurrentUserTags(member);
                    double similarity = calculateSimilarity(userTags, memberTags);
                    return new MemberMatchInfo(member, similarity);
                })
                .filter(info -> info.getSimilarity() > 0)
                .sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
                .limit(20)
                .collect(Collectors.toList());
    }

    /**
     * 회원의 선호 태그(preferredTags) 반환
     */
    private Set<String> getCurrentUserTags(Member member) {
        if (member.getPreferredTags() == null || member.getPreferredTags().isEmpty()) {
            return new HashSet<>();
        }

        return member.getPreferredTags().stream()
                .map(ScheduleTag::getDisplayName)
                .collect(Collectors.toSet());
    }

    /**
     * 특정 지역에 일정이 있는지 확인
     */
    private boolean hasScheduleInRegion(Member member, String region) {
        if (member.getSchedules() == null || member.getSchedules().isEmpty()) {
            return false;
        }

        return member.getSchedules().stream()
                .anyMatch(schedule -> schedule.getRegion() != null && 
                          schedule.getRegion().getDisplayName().equals(region));
    }

    /**
     * 두 태그 집합의 유사도 계산 (Jaccard 유사도)
     */
    private double calculateSimilarity(Set<String> tags1, Set<String> tags2) {
        if (tags1.isEmpty() && tags2.isEmpty()) {
            return 0;
        }

        Set<String> intersection = new HashSet<>(tags1);
        intersection.retainAll(tags2);

        Set<String> union = new HashSet<>(tags1);
        union.addAll(tags2);

        return (double) intersection.size() / union.size();
    }

    /**
     * 매칭 정보 DTO
     */
    @lombok.Getter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MemberMatchInfo {
        private Long id;
        private String nickname;
        private String email;
        private String profileImage;
        private double similarity;

        public MemberMatchInfo(Member member, double similarity) {
            this.id = member.getId();
            this.nickname = member.getNickname();
            this.email = member.getEmail();
            this.profileImage = member.getProfileImage();
            this.similarity = Math.round(similarity * 100.0) / 100.0;  // 소수점 2자리
        }
    }
}