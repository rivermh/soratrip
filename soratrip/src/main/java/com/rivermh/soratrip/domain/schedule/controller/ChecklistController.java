package com.rivermh.soratrip.domain.schedule.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.service.ChecklistService;
import com.rivermh.soratrip.domain.schedule.service.TravelScheduleService;
import com.rivermh.soratrip.global.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/schedules/{scheduleId}/checklist")
@RequiredArgsConstructor
public class ChecklistController {

    private final ChecklistService checklistService;
    private final TravelScheduleService travelScheduleService;

    // 자주 챙기는 항목 원클릭 추가용 (메시지 키 목록, 표시 텍스트는 템플릿에서 #{} 로 해석)
    private static final List<String> SUGGESTION_KEYS = List.of(
            "schedule.checklist.suggestion.passport",
            "schedule.checklist.suggestion.charger",
            "schedule.checklist.suggestion.medicine",
            "schedule.checklist.suggestion.umbrella",
            "schedule.checklist.suggestion.insurance",
            "schedule.checklist.suggestion.sim"
    );

    // 여행 준비물 체크리스트 (일정 하나당 공유되는 단일 리스트, 소유자만 추가/체크/삭제 가능)
    @GetMapping
    public String checklistPage(@PathVariable("scheduleId") Long scheduleId,
                                @AuthenticationPrincipal Object principal,
                                Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(scheduleId);

        String email = principal != null ? SecurityUtils.extractEmail(principal) : null;
        boolean owner = schedule.isOwnedBy(email);

        if (!schedule.isPublic() && !owner) {
            return "redirect:/schedules";
        }

        model.addAttribute("schedule", schedule);
        model.addAttribute("owner", owner);
        model.addAttribute("items", checklistService.getItems(scheduleId));
        model.addAttribute("suggestionKeys", SUGGESTION_KEYS);
        return "schedule/checklist";
    }

    // 항목 추가
    @PostMapping
    public String addItem(@PathVariable("scheduleId") Long scheduleId,
                          @RequestParam("content") String content,
                          Principal principal) {
        checklistService.addItem(scheduleId, principal.getName(), content);
        return "redirect:/schedules/" + scheduleId + "/checklist";
    }

    // 체크 토글
    @PostMapping("/{itemId}/toggle")
    public String toggleItem(@PathVariable("scheduleId") Long scheduleId,
                             @PathVariable("itemId") Long itemId,
                             Principal principal) {
        checklistService.toggleItem(itemId, principal.getName());
        return "redirect:/schedules/" + scheduleId + "/checklist";
    }

    // 항목 삭제
    @PostMapping("/{itemId}/delete")
    public String deleteItem(@PathVariable("scheduleId") Long scheduleId,
                             @PathVariable("itemId") Long itemId,
                             Principal principal) {
        checklistService.deleteItem(itemId, principal.getName());
        return "redirect:/schedules/" + scheduleId + "/checklist";
    }
}
