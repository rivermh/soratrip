package com.rivermh.soratrip.domain.expense.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.rivermh.soratrip.domain.expense.dto.ExpenseCreateDto;
import com.rivermh.soratrip.domain.expense.service.ExpenseService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/schedules/{scheduleId}")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    // 지출 등록
    @PostMapping("/days/{dayId}/expenses")
    public String createExpense(@PathVariable("scheduleId") Long scheduleId,
                                @PathVariable("dayId") Long dayId,
                                @ModelAttribute ExpenseCreateDto dto,
                                Principal principal) {
        expenseService.createExpense(scheduleId, dayId, dto, principal.getName());
        return "redirect:/schedules/" + scheduleId;
    }

    // 지출 삭제
    @PostMapping("/days/{dayId}/expenses/{expenseId}/delete")
    public String deleteExpense(@PathVariable("scheduleId") Long scheduleId,
                                @PathVariable("dayId") Long dayId,
                                @PathVariable("expenseId") Long expenseId,
                                Principal principal) {
        expenseService.deleteExpense(expenseId, principal.getName());
        return "redirect:/schedules/" + scheduleId;
    }

    // 일정 전체 가계부 (총액 + 카테고리별 집계)
    @GetMapping("/expenses")
    public String scheduleLedger(@PathVariable("scheduleId") Long scheduleId, Model model) {
        model.addAttribute("scheduleId", scheduleId);
        model.addAttribute("summary", expenseService.getScheduleSummary(scheduleId));
        model.addAttribute("expenses", expenseService.getExpensesForSchedule(scheduleId));
        return "expense/ledger";
    }
}
