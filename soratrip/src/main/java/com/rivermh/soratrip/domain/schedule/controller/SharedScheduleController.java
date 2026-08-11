package com.rivermh.soratrip.domain.schedule.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.service.TravelScheduleService;

import lombok.RequiredArgsConstructor;

// 로그인 없이 공유 링크로 일정을 읽기 전용으로 열람하는 컨트롤러
@Controller
@RequiredArgsConstructor
public class SharedScheduleController {

    private final TravelScheduleService travelScheduleService;

    @GetMapping("/shared/{token}")
    public String viewSharedSchedule(@PathVariable("token") String token, Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleByShareToken(token);
        model.addAttribute("schedule", schedule);
        return "schedule/shared";
    }
}
