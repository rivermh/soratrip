package com.rivermh.soratrip.domain.schedule.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.expense.entity.Expense;
import com.rivermh.soratrip.domain.expense.entity.ExpenseCategory;
import com.rivermh.soratrip.domain.expense.repository.ExpenseCategoryTotal;
import com.rivermh.soratrip.domain.expense.repository.ExpenseRepository;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.photo.entity.Photo;
import com.rivermh.soratrip.domain.photo.repository.PhotoRepository;
import com.rivermh.soratrip.domain.review.entity.Review;
import com.rivermh.soratrip.domain.review.repository.ReviewRepository;
import com.rivermh.soratrip.domain.schedule.dto.ScheduleJournalDto;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SchedulePortfolioService {

    private final TravelScheduleRepository travelScheduleRepository;
    private final MemberRepository memberRepository;
    private final ExpenseRepository expenseRepository;
    private final PhotoRepository photoRepository;
    private final ReviewRepository reviewRepository;

    // 여행 일지: 일정 + 일자별 지출/사진/후기 + 전체 요약을 한 번에 집계 (일자마다 개별 조회하지 않음, N+1 방지)
    public ScheduleJournalDto getJournal(Long scheduleId) {
        TravelSchedule schedule = travelScheduleRepository.findByIdWithDetails(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("해당 일정을 찾을 수 없습니다. id=" + scheduleId));

        Map<Long, List<Expense>> expensesByDay = expenseRepository.findByTravelScheduleIdWithDay(scheduleId).stream()
                .collect(Collectors.groupingBy(e -> e.getScheduleDay().getId()));
        Map<Long, List<Photo>> photosByDay = photoRepository.findByTravelScheduleIdWithDay(scheduleId).stream()
                .collect(Collectors.groupingBy(p -> p.getScheduleDay().getId()));
        Map<Long, Review> reviewByDay = reviewRepository.findByTravelScheduleIdWithDay(scheduleId).stream()
                .collect(Collectors.toMap(
                        r -> r.getScheduleDay().getId(), 
                        r -> r, 
                        (existing, replacement) -> existing // 중복 키 발생 시 기존 리뷰 유지
                ));

        BigDecimal total = expenseRepository.sumTotalByScheduleId(scheduleId);
        BigDecimal totalJpy = expenseRepository.sumJpyTotalByScheduleId(scheduleId);
        Map<ExpenseCategory, BigDecimal> byCategory = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategoryTotal row : expenseRepository.sumByCategory(scheduleId)) {
            byCategory.put(row.getCategory(), row.getTotal());
        }

        return new ScheduleJournalDto(schedule, expensesByDay, photosByDay, reviewByDay, total, totalJpy, byCategory);
    }

    // 내가 완료한 여행 목록 (마이페이지, 공개 여부 무관)
    public List<TravelSchedule> getMyCompletedTrips(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        return travelScheduleRepository.findByMemberIdAndEndDateBeforeOrderByEndDateDesc(member.getId(), LocalDate.now());
    }

    // 특정 회원의 공개된 완료 여행 목록 (포트폴리오)
    public List<TravelSchedule> getPublicPortfolio(Long memberId) {
        return travelScheduleRepository.findByMemberIdAndIsPublicTrueAndEndDateBeforeOrderByEndDateDesc(memberId, LocalDate.now());
    }
}
