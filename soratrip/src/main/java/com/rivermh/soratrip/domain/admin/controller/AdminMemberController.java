package com.rivermh.soratrip.domain.admin.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.entity.MemberStatus;
import com.rivermh.soratrip.domain.member.entity.Role;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.member.service.MemberService;
import com.rivermh.soratrip.global.security.SecurityUtils;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/members")
public class AdminMemberController {

    private final MemberRepository memberRepository;
    private final MemberService memberService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
                        Model model) {
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        Page<Member> members = memberRepository.searchMembers(searchKeyword, pageable);

        model.addAttribute("members", members);
        model.addAttribute("keyword", keyword);
        return "admin/members";
    }

    @PostMapping("/{id}/role")
    public String changeRole(@PathVariable Long id, @RequestParam Role role,
                              @AuthenticationPrincipal Object principal) {
        String adminEmail = SecurityUtils.requireEmail(principal);
        memberService.updateRole(id, role, adminEmail);
        return "redirect:/admin/members";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam MemberStatus status,
                                @AuthenticationPrincipal Object principal) {
        String adminEmail = SecurityUtils.requireEmail(principal);
        memberService.updateStatus(id, status, adminEmail);
        return "redirect:/admin/members";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal Object principal) {
        String adminEmail = SecurityUtils.requireEmail(principal);
        memberService.adminDeleteMember(id, adminEmail);
        return "redirect:/admin/members";
    }
}
