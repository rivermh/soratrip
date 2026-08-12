package com.rivermh.soratrip.domain.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.rivermh.soratrip.domain.admin.service.AdminStatsService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminController {

    private final AdminStatsService adminStatsService;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", adminStatsService.getStats());
        return "admin/dashboard";
    }
}
