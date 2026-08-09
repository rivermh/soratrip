package com.rivermh.soratrip.domain.schedule.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.photo.entity.Photo;
import com.rivermh.soratrip.domain.photo.repository.PhotoRepository;
import com.rivermh.soratrip.domain.schedule.dto.ScheduleJournalDto;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.service.SchedulePortfolioService;
import com.rivermh.soratrip.global.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class SchedulePortfolioController {

    private final SchedulePortfolioService schedulePortfolioService;
    private final MemberRepository memberRepository;
    private final PhotoRepository photoRepository;

    // 여행 일지 (일정 + 지출 + 사진 + 후기 통합 조회)
    @GetMapping("/schedules/{id}/journal")
    public String journal(@PathVariable("id") Long id,
                          @AuthenticationPrincipal Object principal,
                          Model model) {
        ScheduleJournalDto journal = schedulePortfolioService.getJournal(id);
        TravelSchedule schedule = journal.getSchedule();

        String email = principal != null ? SecurityUtils.extractEmail(principal) : null;
        boolean owner = schedule.isOwnedBy(email);
        if (!schedule.isPublic() && !owner) {
            return "redirect:/schedules";
        }

        model.addAttribute("journal", journal);
        model.addAttribute("owner", owner);
        return "schedule/journal";
    }

    // 특정 회원의 여행 포트폴리오 (공개된 완료 여행 목록)
    @GetMapping("/portfolio/{memberId}")
    public String portfolio(@PathVariable("memberId") Long memberId, Model model) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        List<TravelSchedule> trips = schedulePortfolioService.getPublicPortfolio(memberId);

        // 여행별 대표 사진 (최초 업로드 사진) 조회 - 완료 여행 수가 많지 않은 전제하의 단순 조회
        Map<Long, Photo> coverPhotoBySchedule = new HashMap<>();
        for (TravelSchedule trip : trips) {
            photoRepository.findFirstByTravelScheduleIdOrderByIdAsc(trip.getId())
                    .ifPresent(photo -> coverPhotoBySchedule.put(trip.getId(), photo));
        }

        model.addAttribute("member", member);
        model.addAttribute("trips", trips);
        model.addAttribute("coverPhotoBySchedule", coverPhotoBySchedule);
        return "schedule/portfolio";
    }
}
