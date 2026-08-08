package com.rivermh.soratrip.domain.bookmark.service;

import com.rivermh.soratrip.domain.bookmark.dto.BookmarkToggleResponse;
import com.rivermh.soratrip.domain.bookmark.entity.ScheduleBookmark;
import com.rivermh.soratrip.domain.bookmark.repository.ScheduleBookmarkRepository;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.repository.TravelScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class BookmarkService {

    private final ScheduleBookmarkRepository scheduleBookmarkRepository;
    private final TravelScheduleRepository travelScheduleRepository;
    private final MemberRepository memberRepository;

    public BookmarkToggleResponse toggleScheduleBookmark(Long scheduleId, String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        TravelSchedule schedule = travelScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 일정입니다."));

        Optional<ScheduleBookmark> bookmark = scheduleBookmarkRepository.findByMemberAndTravelSchedule(member, schedule);
        boolean isBookmarked;

        if (bookmark.isPresent()) {
            scheduleBookmarkRepository.delete(bookmark.get());
            isBookmarked = false;
        } else {
            scheduleBookmarkRepository.save(ScheduleBookmark.builder()
                    .member(member)
                    .travelSchedule(schedule)
                    .build());
            isBookmarked = true;
        }

        int currentBookmarkCount = scheduleBookmarkRepository.countByTravelSchedule(schedule);
        return new BookmarkToggleResponse(isBookmarked, currentBookmarkCount);
    }
}