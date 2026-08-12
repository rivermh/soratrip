package com.rivermh.soratrip.domain.admin.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.rivermh.soratrip.domain.admin.dto.AdminStatsDto;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.post.repository.PostRepository;
import com.rivermh.soratrip.domain.report.entity.ReportStatus;
import com.rivermh.soratrip.domain.report.repository.PostReportRepository;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final TravelScheduleRepository travelScheduleRepository;
    private final PostReportRepository postReportRepository;

    // 관리자 대시보드 통계 조회. AI 생성 일정 여부를 별도로 추적하는 컬럼이 없어서
    // "일정 생성 수"는 AI/수동 구분 없이 전체 TravelSchedule 수를 프록시로 사용한다.
    public AdminStatsDto getStats() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        return new AdminStatsDto(
                memberRepository.count(),
                memberRepository.countByCreatedAtAfter(weekAgo),
                postRepository.count(),
                postRepository.countByCreatedAtAfter(weekAgo),
                travelScheduleRepository.count(),
                travelScheduleRepository.countByCreatedAtAfter(weekAgo),
                postReportRepository.countByStatus(ReportStatus.PENDING)
        );
    }
}
