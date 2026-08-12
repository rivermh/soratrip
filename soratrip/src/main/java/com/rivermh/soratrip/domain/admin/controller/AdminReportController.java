package com.rivermh.soratrip.domain.admin.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.rivermh.soratrip.domain.report.entity.PostReport;
import com.rivermh.soratrip.domain.report.service.ReportService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/reports")
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    public String list(@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
                        Model model) {
        Page<PostReport> reports = reportService.getPendingReports(pageable);
        model.addAttribute("reports", reports);
        return "admin/reports";
    }

    @PostMapping("/{id}/resolve")
    public String resolve(@PathVariable Long id) {
        reportService.resolveReport(id);
        return "redirect:/admin/reports";
    }
}
