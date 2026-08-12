package com.rivermh.soratrip.domain.schedule.controller;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.rivermh.soratrip.domain.bookmark.repository.ScheduleBookmarkRepository;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.review.dto.MobilityScoreDto;
import com.rivermh.soratrip.domain.review.entity.Review;
import com.rivermh.soratrip.domain.review.service.ReviewService;
import com.rivermh.soratrip.domain.schedule.dto.AiScheduleRequest;
import com.rivermh.soratrip.domain.schedule.dto.DayWeatherSummaryDto;
import com.rivermh.soratrip.domain.schedule.dto.TravelScheduleForm;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.service.AffiliateLinkService;
import com.rivermh.soratrip.domain.schedule.service.ClaudeScheduleService;
import com.rivermh.soratrip.domain.schedule.service.ScheduleExportService;
import com.rivermh.soratrip.domain.schedule.service.ScheduleRecommendationService;
import com.rivermh.soratrip.domain.schedule.service.TravelScheduleService;
import com.rivermh.soratrip.domain.schedule.service.WeatherService;
import com.rivermh.soratrip.global.file.FileStorageService;
import com.rivermh.soratrip.global.security.SecurityUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class TravelScheduleController {

    private final TravelScheduleService travelScheduleService;
    private final ScheduleRecommendationService scheduleRecommendationService;
    private final ReviewService reviewService;
    private final ClaudeScheduleService claudeScheduleService;
    private final WeatherService weatherService;
    private final ScheduleExportService scheduleExportService;
    private final AffiliateLinkService affiliateLinkService;

    // 북마크 조회를 위해 추가된 의존성
    private final ScheduleBookmarkRepository scheduleBookmarkRepository;
    private final MemberRepository memberRepository;
    private final FileStorageService fileStorageService;

    // 1. 내 여행 일정 목록 페이지
    @GetMapping
    public String listSchedules(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/members/login";
        }

        List<TravelSchedule> schedules = travelScheduleService.getMySchedulesByEmail(principal.getName());
        model.addAttribute("schedules", schedules);
        return "schedule/list";
    }

    // 2. 여행 일정 작성 폼 페이지
    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("scheduleForm", new TravelScheduleForm());
        model.addAttribute("regions", Region.values());
        return "schedule/form";
    }

    // 3. 여행 일정 저장 처리
    @PostMapping("/new")
    public String createSchedule(@Valid @ModelAttribute("scheduleForm") TravelScheduleForm form,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal Object principal,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("regions", Region.values());
            return "schedule/form";
        }

        String email = SecurityUtils.extractEmail(principal);
        if (email == null) {
            return "redirect:/members/login";
        }

        Long scheduleId = travelScheduleService.createSchedule(form, email);
        return "redirect:/schedules/" + scheduleId;
    }

    // 4. 다른 회원의 공개 일정 둘러보기 (지역/태그 필터 + 페이징)
    @GetMapping("/explore")
    public String explore(@RequestParam(name = "region", required = false) Region region,
                          @RequestParam(name = "tag", required = false) ScheduleTag tag,
                          @PageableDefault(size = 12, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
                          Model model) {
        Page<TravelSchedule> schedules = travelScheduleService.searchPublic(region, tag, pageable);

        List<Long> scheduleIds = schedules.getContent().stream().map(TravelSchedule::getId).collect(Collectors.toList());

        model.addAttribute("schedules", schedules);
        model.addAttribute("regions", Region.values());
        model.addAttribute("tags", ScheduleTag.values());
        model.addAttribute("selectedRegion", region);
        model.addAttribute("selectedTag", tag);
        model.addAttribute("mobilityScores", reviewService.getMobilityScores(scheduleIds));
        return "schedule/explore";
    }

    // 4-1. 취향 태그 기반 맞춤 일정 추천
    @GetMapping("/recommended")
    public String recommended(Model model, @AuthenticationPrincipal Object principal) {
        String email = SecurityUtils.extractEmail(principal);
        if (email == null) {
            return "redirect:/members/login";
        }

        List<TravelSchedule> recommended = scheduleRecommendationService.recommendFor(email);
        List<Long> scheduleIds = recommended.stream().map(TravelSchedule::getId).collect(Collectors.toList());

        model.addAttribute("schedules", recommended);
        model.addAttribute("mobilityScores", reviewService.getMobilityScores(scheduleIds));
        return "schedule/recommended";
    }

    // 5. 여행 일정 상세 보기 페이지 (Google Maps 및 북마크 연동)
    @GetMapping("/{id}")
    public String detailSchedule(@PathVariable("id") Long id,
                                 @AuthenticationPrincipal Object principal,
                                 Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(id);

        String email = principal != null ? SecurityUtils.extractEmail(principal) : null;
        boolean owner = schedule.isOwnedBy(email);

        if (!schedule.isPublic() && !owner) {
            return "redirect:/schedules";
        }

        boolean bookmarked = false;
        if (email != null) {
            Member member = memberRepository.findByEmail(email).orElse(null);
            if (member != null) {
                bookmarked = scheduleBookmarkRepository.existsByMemberAndTravelSchedule(member, schedule);
            }
        }
        int bookmarkCount = scheduleBookmarkRepository.countByTravelSchedule(schedule);

        List<Review> reviews = reviewService.getReviewsForSchedule(id);

        model.addAttribute("schedule", schedule);
        model.addAttribute("owner", owner);
        model.addAttribute("bookmarked", bookmarked);
        model.addAttribute("bookmarkCount", bookmarkCount);
        model.addAttribute("mobilityScore", MobilityScoreDto.fromReviews(reviews));
        model.addAttribute("agodaLink", affiliateLinkService.buildAgodaLink(schedule.getRegion(), schedule.getStartDate(), schedule.getEndDate()));
        model.addAttribute("bookingLink", affiliateLinkService.buildBookingLink(schedule.getRegion(), schedule.getStartDate(), schedule.getEndDate()));
        return "schedule/detail";
    }

    // 6. 일정 복사 - 새 시작일 입력 폼
    @GetMapping("/{id}/copy")
    public String copyForm(@PathVariable("id") Long id, Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(id);
        model.addAttribute("schedule", schedule);
        return "schedule/copy-form";
    }

    // 7. 일정 복사 처리
    @PostMapping("/{id}/copy")
    public String copySchedule(@PathVariable("id") Long id,
                               @RequestParam("newStartDate") LocalDate newStartDate,
                               Principal principal) {
        Long copiedId = travelScheduleService.copySchedule(id, newStartDate, principal.getName());
        return "redirect:/schedules/" + copiedId;
    }

    // 8. 일정 공개 설정 / 태그 편집 폼
    @GetMapping("/{id}/settings")
    public String settingsForm(@PathVariable("id") Long id, Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(id);
        model.addAttribute("schedule", schedule);
        model.addAttribute("tags", ScheduleTag.values());
        return "schedule/settings";
    }

    // 9. 일정 공개 설정 / 태그 / 예산 저장 처리
    @PostMapping("/{id}/settings")
    public String updateSettings(@PathVariable("id") Long id,
                                 @RequestParam(name = "isPublic", required = false) Boolean isPublic,
                                 @RequestParam(name = "tags", required = false) Set<ScheduleTag> tags,
                                 @RequestParam(name = "budgetKrw", required = false) BigDecimal budgetKrw,
                                 Principal principal) {
        travelScheduleService.updateSettings(id, principal.getName(),
                Boolean.TRUE.equals(isPublic),
                tags != null ? tags : new HashSet<>(),
                budgetKrw);
        return "redirect:/schedules/" + id;
    }

    // 9-0. 일정 삭제 (소유자 전용). 사진 실물 파일은 DB 삭제 트랜잭션 커밋 후 디스크에서 지운다.
    @PostMapping("/{id}/delete")
    public String deleteSchedule(@PathVariable("id") Long id, Principal principal) {
        List<String> photoUrls = travelScheduleService.deleteSchedule(id, principal.getName());
        photoUrls.forEach(fileStorageService::delete);
        return "redirect:/schedules";
    }

    // 9-1. 공유 링크 발급 (소유자 전용)
    @PostMapping("/{id}/share")
    public String createShareLink(@PathVariable("id") Long id, Principal principal) {
        travelScheduleService.createShareLink(id, principal.getName());
        return "redirect:/schedules/" + id + "/settings";
    }

    // 9-2. 공유 링크 폐기 (소유자 전용)
    @PostMapping("/{id}/share/revoke")
    public String revokeShareLink(@PathVariable("id") Long id, Principal principal) {
        travelScheduleService.revokeShareLink(id, principal.getName());
        return "redirect:/schedules/" + id + "/settings";
    }

    // 10. AI 일정 자동 생성 폼
    @GetMapping("/ai-new")
    public String aiCreateForm(Model model) {
        AiScheduleRequest aiRequest = new AiScheduleRequest();
        aiRequest.setStartDate(LocalDate.now().plusDays(1));
        model.addAttribute("aiRequest", aiRequest);
        model.addAttribute("regions", Region.values());
        model.addAttribute("tags", ScheduleTag.values());
        return "schedule/ai-form";
    }

    // AI 일정 자동 생성 처리
    @PostMapping("/ai-new")
    public String createAiSchedule(@Valid @ModelAttribute("aiRequest") AiScheduleRequest aiRequest,
                                   BindingResult bindingResult,
                                   Model model,
                                   @AuthenticationPrincipal Object principal) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("regions", Region.values());
            model.addAttribute("tags", ScheduleTag.values());
            return "schedule/ai-form";
        }

        String email = SecurityUtils.extractEmail(principal);
        if (email == null) {
            return "redirect:/members/login";
        }

        Long scheduleId = claudeScheduleService.createScheduleWithAi(aiRequest, email);
        return "redirect:/schedules/" + scheduleId;
    }

    // 11. 일정별 날씨 및 준비물 팁 페이지 (수정 반영)
    @GetMapping("/{id}/weather")
    public String scheduleWeatherView(@PathVariable("id") Long id,
                                      @AuthenticationPrincipal Object principal,
                                      Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(id);

        String email = principal != null ? SecurityUtils.extractEmail(principal) : null;
        boolean owner = schedule.isOwnedBy(email);

        // 비공개 일정이고 본인 일정이 아닐 경우 접근 제한
        if (!schedule.isPublic() && !owner) {
            return "redirect:/schedules";
        }

        List<DayWeatherSummaryDto> weatherList = weatherService.getScheduleWeatherSummary(id);

        model.addAttribute("schedule", schedule);
        model.addAttribute("weatherList", weatherList);
        model.addAttribute("owner", owner);

        return "schedule/weather";
    }

    // 12. 일정 캘린더(.ics) 다운로드 — 구글/애플/아웃룩 캘린더로 가져오기 가능
    @GetMapping("/{id}/calendar.ics")
    public ResponseEntity<byte[]> downloadIcs(@PathVariable("id") Long id,
                                              @AuthenticationPrincipal Object principal) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(id);

        String email = principal != null ? SecurityUtils.extractEmail(principal) : null;
        if (!schedule.isPublic() && !schedule.isOwnedBy(email)) {
            return ResponseEntity.notFound().build();
        }

        String ics = scheduleExportService.generateIcs(schedule);
        byte[] body = ics.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("soratrip-" + schedule.getId() + ".ics", StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .body(body);
    }

    // 13. 일정 PDF 다운로드
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable("id") Long id,
                                              @AuthenticationPrincipal Object principal) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(id);

        String email = principal != null ? SecurityUtils.extractEmail(principal) : null;
        if (!schedule.isPublic() && !schedule.isOwnedBy(email)) {
            return ResponseEntity.notFound().build();
        }

        Locale locale = LocaleContextHolder.getLocale();
        byte[] pdf = scheduleExportService.generatePdf(schedule, locale);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("soratrip-" + schedule.getId() + ".pdf", StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}