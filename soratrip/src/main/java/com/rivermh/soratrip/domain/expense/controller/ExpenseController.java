package com.rivermh.soratrip.domain.expense.controller;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.rivermh.soratrip.domain.expense.dto.ExpenseCreateDto;
import com.rivermh.soratrip.domain.expense.entity.Expense;
import com.rivermh.soratrip.domain.expense.entity.ExpenseCategory;
import com.rivermh.soratrip.domain.expense.service.ExpenseService;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;
import com.rivermh.soratrip.domain.schedule.service.TravelScheduleService;
import com.rivermh.soratrip.domain.settlement.service.SettlementService;
import com.rivermh.soratrip.global.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/schedules/{scheduleId}")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final TravelScheduleService travelScheduleService;
    private final SettlementService settlementService;

    // 지출 등록
    @PostMapping("/days/{dayId}/expenses")
    public String createExpense(@PathVariable("scheduleId") Long scheduleId,
                                @PathVariable("dayId") Long dayId,
                                @ModelAttribute ExpenseCreateDto dto,
                                Principal principal) {
        expenseService.createExpense(scheduleId, dayId, dto, principal.getName());
        return "redirect:/schedules/" + scheduleId + "/expenses";
    }

    // 지출 삭제
    @PostMapping("/days/{dayId}/expenses/{expenseId}/delete")
    public String deleteExpense(@PathVariable("scheduleId") Long scheduleId,
                                @PathVariable("dayId") Long dayId,
                                @PathVariable("expenseId") Long expenseId,
                                Principal principal) {
        expenseService.deleteExpense(expenseId, principal.getName());
        return "redirect:/schedules/" + scheduleId + "/expenses";
    }

    // 일정 전체 가계부 (총액 + 카테고리별 집계, 일자별 입력/내역, 소유자만 등록/삭제 가능)
    @GetMapping("/expenses")
    public String scheduleLedger(@PathVariable("scheduleId") Long scheduleId,
                                 @AuthenticationPrincipal Object principal,
                                 Model model) {
        TravelSchedule schedule = travelScheduleService.getScheduleDetail(scheduleId);

        String email = principal != null ? SecurityUtils.extractEmail(principal) : null;
        boolean owner = schedule.isOwnedBy(email);

        if (!schedule.isPublic() && !owner) {
            return "redirect:/schedules";
        }

        List<Expense> expenses = expenseService.getExpensesForSchedule(scheduleId);

        model.addAttribute("schedule", schedule);
        model.addAttribute("owner", owner);
        model.addAttribute("summary", expenseService.getScheduleSummary(scheduleId));
        model.addAttribute("expensesByDay", expenses.stream()
                .collect(Collectors.groupingBy(e -> e.getScheduleDay().getId())));
        model.addAttribute("expenseCategories", ExpenseCategory.values());
        model.addAttribute("participants", settlementService.getParticipants(scheduleId));
        return "expense/ledger";
    }
}
