package com.rivermh.soratrip.domain.schedule.controller;

import com.rivermh.soratrip.domain.schedule.dto.AiScheduleRequest;
import com.rivermh.soratrip.domain.schedule.dto.DayRegenerateRequest;
import com.rivermh.soratrip.domain.schedule.dto.ScheduleItemForm;
import com.rivermh.soratrip.domain.schedule.dto.ScheduleOrderUpdateRequest;
import com.rivermh.soratrip.domain.schedule.service.ClaudeScheduleService;
import com.rivermh.soratrip.domain.schedule.service.TravelScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleApiController {

    private final TravelScheduleService travelScheduleService;
    private final ClaudeScheduleService claudeScheduleService;

    //  AI 여행 일정 생성 API (@Valid 적용)
    @PostMapping("/ai")
    public ResponseEntity<Long> createAiSchedule(
            @RequestBody @Valid AiScheduleRequest request,
            Principal principal) {

        Long scheduleId = claudeScheduleService.createScheduleWithAi(request, principal.getName());
        return ResponseEntity.ok(scheduleId);
    }

    // 특정 하루만 AI로 다시 생성 (기존 dayNumber/visitDate 유지, items만 교체)
    @PostMapping("/days/{dayId}/ai-regenerate")
    public ResponseEntity<String> regenerateDay(
            @PathVariable("dayId") Long dayId,
            @RequestBody(required = false) DayRegenerateRequest request,
            Principal principal) {

        String extraPrompt = request != null ? request.getExtraPrompt() : null;
        claudeScheduleService.regenerateDay(dayId, extraPrompt, principal.getName());
        return ResponseEntity.ok("AI가 이 날 일정을 새로 짜드렸어요.");
    }

    // 기존 일정에 장소 추가 API (일정 상세 페이지에서 사용)
    @PostMapping("/days/{dayId}/items")
    public ResponseEntity<Long> addItem(
            @PathVariable("dayId") Long dayId,
            @RequestBody ScheduleItemForm form,
            Principal principal) {

        Long itemId = travelScheduleService.addScheduleItem(dayId, form, principal.getName());
        return ResponseEntity.ok(itemId);
    }

    // 순서 변경 저장 API
    @PatchMapping("/{scheduleId}/order")
    public ResponseEntity<String> updateOrder(
            @PathVariable("scheduleId") Long scheduleId,
            @RequestBody ScheduleOrderUpdateRequest request,
            Principal principal) {

        travelScheduleService.updateItemOrder(scheduleId, request, principal.getName());
        return ResponseEntity.ok("순서가 정상적으로 저장되었습니다.");
    }

    // 장소 삭제 API
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<String> deleteItem(
            @PathVariable("itemId") Long itemId,
            Principal principal) {

        travelScheduleService.deleteScheduleItem(itemId, principal.getName());
        return ResponseEntity.ok("장소가 삭제되었습니다.");
    }
}