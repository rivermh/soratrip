package com.rivermh.soratrip.domain.schedule.controller;

import com.rivermh.soratrip.domain.schedule.dto.AiScheduleRequest; // 👈 DTO 임포트 추가
import com.rivermh.soratrip.domain.schedule.dto.ScheduleOrderUpdateRequest;
import com.rivermh.soratrip.domain.schedule.service.GroqScheduleService; // 👈 AI 서비스 임포트 추가
import com.rivermh.soratrip.domain.schedule.service.TravelScheduleService;
import jakarta.validation.Valid; // 👈 @Valid 임포트 추가
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleApiController {

    private final TravelScheduleService travelScheduleService;
    private final GroqScheduleService groqScheduleService; // 👈 AI 서비스 주입 추가

    //  AI 여행 일정 생성 API (@Valid 적용)
    @PostMapping("/ai")
    public ResponseEntity<Long> createAiSchedule(
            @RequestBody @Valid AiScheduleRequest request,
            Principal principal) {

        Long scheduleId = groqScheduleService.createScheduleWithAi(request, principal.getName());
        return ResponseEntity.ok(scheduleId);
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