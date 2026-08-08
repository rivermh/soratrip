package com.rivermh.soratrip.domain.schedule.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.rivermh.soratrip.domain.expense.entity.Expense;
import com.rivermh.soratrip.domain.expense.entity.ExpenseCategory;
import com.rivermh.soratrip.domain.expense.service.ExpenseService;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.photo.entity.Photo;
import com.rivermh.soratrip.domain.photo.service.PhotoService;
import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.review.entity.Review;
import com.rivermh.soratrip.domain.review.service.ReviewService;
import com.rivermh.soratrip.domain.schedule.dto.AiScheduleRequest;
import com.rivermh.soratrip.domain.schedule.dto.TravelScheduleForm;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.service.GroqScheduleService;
import com.rivermh.soratrip.domain.schedule.service.ScheduleRecommendationService;
import com.rivermh.soratrip.domain.schedule.service.TravelScheduleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class TravelScheduleController {

    private final TravelScheduleService travelScheduleService;
    private final ScheduleRecommendationService scheduleRecommendationService;
    private final ExpenseService expenseService;
    private final PhotoService photoService;
    private final ReviewService reviewService;
    private final GroqScheduleService groqScheduleService;
    
    // 북마크 조회를 위해 추가된 의존성
    private final ScheduleBookmarkRepository scheduleBookmarkRepository;
    private final MemberRepository memberRepository;

    // 1. 내 여행 일정 목록 페이지
    @GetMapping
    public String listSchedules(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/members/login";
        }

        // 현재 로그인한 사용자의 일정 목록 가져오기
        // Member 엔티티 가져오는 방식에 맞게 조정 필요 (email 기준 조회)
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
                                 @AuthenticationPrincipal Object principal, // Principal 대신 Object로 수신
                                 Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("regions", Region.values());
            return "schedule/form";
        }

        String email = extractEmail(principal); // 안전하게 이메일 추출
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

        model.addAttribute("schedules", schedules);
        model.addAttribute("regions", Region.values());
        model.addAttribute("tags", ScheduleTag.values());
        model.addAttribute("selectedRegion", region);
        model.addAttribute("selectedTag", tag);
        return "schedule/explore";
    }

 // 4-1. 취향 태그 기반 맞춤 일정 추천 (로그인 검증 및 소셜/일반 로그인 통합 이메일 추출)
    @GetMapping("/recommended")
    public String recommended(Model model, @AuthenticationPrincipal Object principal) {
        String email = extractEmail(principal);
        if (email == null) {
            return "redirect:/members/login";
        }

        model.addAttribute("schedules", scheduleRecommendationService.recommendFor(email));
        return "schedule/recommended";
    }

 // 5. 여행 일정 상세 보기 페이지 (Google Maps 및 북마크 연동)
    @GetMapping("/{id}")
    public String detailSchedule(@PathVariable("id") Long id,
                                 @AuthenticationPrincipal Object principal,
                                 Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(id);

        String email = principal != null ? extractEmail(principal) : null;
        boolean owner = email != null && schedule.getMember() != null && schedule.getMember().getEmail().equals(email);

        // [디버깅 로그] 서버 콘솔에서 값을 직접 확인하기 위함
        System.out.println("=== 디버깅 체크 ===");
        System.out.println("로그인 유저 이메일: " + email);
        System.out.println("일정 작성자 이메일: " + (schedule.getMember() != null ? schedule.getMember().getEmail() : "null"));
        System.out.println("공개 여부(isPublic): " + schedule.isPublic());
        System.out.println("소유자 여부(owner): " + owner);

        /* 권한 체크 때문에 튕기는 현상을 확인하기 위해 잠시 주석 처리
        if (!schedule.isPublic() && !owner) {
            return "redirect:/schedules";
        }
        */

        // 북마크 상태 및 총 개수 조회 로직 추가
        boolean bookmarked = false;
        if (email != null) {
            Member member = memberRepository.findByEmail(email).orElse(null);
            if (member != null) {
                bookmarked = scheduleBookmarkRepository.existsByMemberAndTravelSchedule(member, schedule);
            }
        }
        int bookmarkCount = scheduleBookmarkRepository.countByTravelSchedule(schedule);

        // 일자별 지출/사진/후기를 미리 그룹핑해서 전달 (일자마다 개별 조회하는 N+1 방지)
        Map<Long, List<Expense>> expensesByDay = expenseService.getExpensesForSchedule(id).stream()
                .collect(Collectors.groupingBy(e -> e.getScheduleDay().getId()));
        Map<Long, List<Photo>> photosByDay = photoService.getPhotosForSchedule(id).stream()
                .collect(Collectors.groupingBy(p -> p.getScheduleDay().getId()));
        Map<Long, Review> reviewByDay = reviewService.getReviewsForSchedule(id).stream()
                .collect(Collectors.toMap(
                        r -> r.getScheduleDay().getId(), 
                        r -> r, 
                        (existing, replacement) -> existing // 중복 키 발생 시 기존 리뷰 유지
                ));

        model.addAttribute("schedule", schedule);
        model.addAttribute("owner", owner);
        model.addAttribute("bookmarked", bookmarked);       // 추가된 북마크 여부
        model.addAttribute("bookmarkCount", bookmarkCount); // 추가된 북마크 총 개수
        model.addAttribute("expensesByDay", expensesByDay);
        model.addAttribute("photosByDay", photosByDay);
        model.addAttribute("reviewByDay", reviewByDay);
        model.addAttribute("expenseSummary", expenseService.getScheduleSummary(id));
        model.addAttribute("expenseCategories", ExpenseCategory.values());
        return "schedule/detail";
    }

    // 6. 일정 복사 - 새 시작일 입력 폼
    @GetMapping("/{id}/copy")
    public String copyForm(@PathVariable("id") Long id, Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(id);
        model.addAttribute("schedule", schedule);
        return "schedule/copy-form";
    }

    // 7. 일정 복사 처리 (다른 사람의 일정을 내 일정으로 가져오기)
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

    // 9. 일정 공개 설정 / 태그 저장 처리
    @PostMapping("/{id}/settings")
    public String updateSettings(@PathVariable("id") Long id,
                                 @RequestParam(name = "isPublic", required = false) Boolean isPublic,
                                 @RequestParam(name = "tags", required = false) Set<ScheduleTag> tags,
                                 Principal principal) {
        travelScheduleService.updateSettings(id, principal.getName(),
                Boolean.TRUE.equals(isPublic),
                tags != null ? tags : new HashSet<>());
        return "redirect:/schedules/" + id;
    }

    // 로그인한 유저의 이메일을 뽑아내는 헬퍼 메서드 (일반로그인/소셜로그인 공통 대응)
    private String extractEmail(Object principal) {
        if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return null;
    }
    
 // 10. AI 일정 자동 생성 폼
    @GetMapping("/ai-new")
    public String aiCreateForm(Model model) {
        model.addAttribute("aiRequest", new AiScheduleRequest());
        model.addAttribute("regions", Region.values());
        model.addAttribute("tags", ScheduleTag.values());
        return "schedule/ai-form";
    }

 // AI 일정 자동 생성 처리
    @PostMapping("/ai-new")
    public String createAiSchedule(@Valid @ModelAttribute("aiRequest") AiScheduleRequest aiRequest,
                                   BindingResult bindingResult,
                                   Model model, // 👈 Model 추가
                                   @AuthenticationPrincipal Object principal) {
        
        // 💡 유효성 검사 실패 시
        if (bindingResult.hasErrors()) {
            // ⚠️ 폼 화면을 다시 그릴 때 필요한 데이터들을 다시 넣어주어야 에러 x
            model.addAttribute("regions", Region.values()); // 본인 프로젝트의 지역 목록 Enum에 맞게 수정
            model.addAttribute("tags", ScheduleTag.values()); // 본인 프로젝트의 태그 목록 Enum에 맞게 수정
            return "schedule/ai-form"; 
        }

        String email = extractEmail(principal);
        if (email == null) {
            return "redirect:/members/login";
        }

        Long scheduleId = groqScheduleService.createScheduleWithAi(aiRequest, email);
        return "redirect:/schedules/" + scheduleId;
    }
}