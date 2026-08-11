package com.rivermh.soratrip.domain.settlement.controller;

import java.security.Principal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.service.TravelScheduleService;
import com.rivermh.soratrip.domain.settlement.service.SettlementService;
import com.rivermh.soratrip.global.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/schedules/{scheduleId}/settlement")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final TravelScheduleService travelScheduleService;

    // 정산 페이지 (참여자 관리 + 정산 결과), 일정 소유자만 접근 가능
    @GetMapping
    public String settlementPage(@PathVariable("scheduleId") Long scheduleId,
                                 @AuthenticationPrincipal Object principal,
                                 Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(scheduleId);
        String email = SecurityUtils.extractEmail(principal);
        if (!schedule.isOwnedBy(email)) {
            return "redirect:/schedules/" + scheduleId;
        }

        model.addAttribute("schedule", schedule);
        model.addAttribute("participants", settlementService.getParticipants(scheduleId));
        model.addAttribute("result", settlementService.calculateSettlement(scheduleId));
        return "settlement/settlement";
    }

    // 참여자 추가
    @PostMapping("/participants")
    public String addParticipant(@PathVariable("scheduleId") Long scheduleId,
                                 @RequestParam("name") String name,
                                 Principal principal) {
        settlementService.addParticipant(scheduleId, name, principal.getName());
        return "redirect:/schedules/" + scheduleId + "/settlement";
    }

    // 참여자 삭제
    @PostMapping("/participants/{participantId}/delete")
    public String deleteParticipant(@PathVariable("scheduleId") Long scheduleId,
                                    @PathVariable("participantId") Long participantId,
                                    Principal principal) {
        settlementService.removeParticipant(participantId, principal.getName());
        return "redirect:/schedules/" + scheduleId + "/settlement";
    }

    // 정산 결과의 특정 송금 건 완료 표시 토글
    @PostMapping("/transactions/complete")
    public String toggleTransactionCompletion(@PathVariable("scheduleId") Long scheduleId,
                                              @RequestParam("fromParticipantId") Long fromParticipantId,
                                              @RequestParam("toParticipantId") Long toParticipantId,
                                              Principal principal) {
        settlementService.toggleTransactionCompletion(scheduleId, fromParticipantId, toParticipantId, principal.getName());
        return "redirect:/schedules/" + scheduleId + "/settlement";
    }
}
