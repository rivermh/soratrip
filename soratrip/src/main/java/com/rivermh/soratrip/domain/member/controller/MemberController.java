package com.rivermh.soratrip.domain.member.controller;

import java.util.HashSet;
import java.util.Set;

import com.rivermh.soratrip.domain.bookmark.repository.ScheduleBookmarkRepository;
import com.rivermh.soratrip.domain.comment.repository.CommentRepository;
import com.rivermh.soratrip.domain.like.repository.PostLikeRepository;
import com.rivermh.soratrip.domain.like.repository.ScheduleLikeRepository;
import com.rivermh.soratrip.domain.member.dto.MemberJoinDto;
import com.rivermh.soratrip.domain.member.dto.MemberProfileDto;
import com.rivermh.soratrip.domain.member.dto.PasswordChangeDto;
import com.rivermh.soratrip.domain.member.dto.PasswordResetConfirmDto;
import com.rivermh.soratrip.domain.member.dto.PasswordResetRequestDto;
import com.rivermh.soratrip.domain.member.service.MemberService;
import com.rivermh.soratrip.domain.post.repository.PostRepository;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.domain.schedule.service.SchedulePortfolioService;
import com.rivermh.soratrip.global.security.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SchedulePortfolioService schedulePortfolioService;
    private final PostLikeRepository postLikeRepository;
    private final ScheduleLikeRepository scheduleLikeRepository;
    private final ScheduleBookmarkRepository scheduleBookmarkRepository;

    // 회원가입 페이지 요청
    @GetMapping("/join")
    public String joinForm(@ModelAttribute("memberJoinDto") MemberJoinDto memberJoinDto) {
        return "member/join";
    }

    // 회원가입 처리
    @PostMapping("/join")
    public String join(@Valid @ModelAttribute("memberJoinDto") MemberJoinDto dto, 
                       BindingResult bindingResult) {
        
        // 1. 유효성 검증(어노테이션) 실패 시 다시 가입 페이지로
        if (bindingResult.hasErrors()) {
            return "member/join";
        }

        // 2. 비밀번호 일치 검증
        if (!dto.isPasswordMatched()) {
            bindingResult.rejectValue("passwordConfirm", "passwordMismatch", "비밀번호가 일치하지 않습니다.");
            return "member/join";
        }

        try {
            memberService.join(dto);
        } catch (IllegalArgumentException e) {
            // 비즈니스 예외(이메일 중복 등) 발생 시 에러 메시지 등록
            bindingResult.rejectValue("email", "emailDuplicate", e.getMessage());
            return "member/join";
        }

        // 회원가입 성공 → 완료 페이지로 이동
        return "redirect:/members/signup-success";
    }

    // 회원가입 완료 페이지
    @GetMapping("/signup-success")
    public String signupSuccess() {
        return "member/signup-success";
    }

    // 로그인 페이지 요청
    @GetMapping("/login")
    public String loginForm() {
        return "member/login";
    }

    // 비밀번호 재설정 요청 페이지 (이메일 입력)
    @GetMapping("/password-reset")
    public String passwordResetForm(@ModelAttribute("passwordResetRequestDto") PasswordResetRequestDto dto) {
        return "member/password-reset-request";
    }

    // 비밀번호 재설정 요청 처리 (인증 코드 발송)
    @PostMapping("/password-reset")
    public String passwordReset(@Valid @ModelAttribute("passwordResetRequestDto") PasswordResetRequestDto dto,
                                BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "member/password-reset-request";
        }

        // 가입 여부/소셜 계정 여부를 노출하면 계정 열거(user enumeration) 공격에 악용될 수 있어,
        // 실패 사유와 무관하게 항상 다음 단계로 진행한다 (존재하지 않는 이메일이면 조용히 메일만 보내지 않고,
        // 메일 발송 자체가 실패하더라도 - 예: Resend 샌드박스 제한 - 사용자에게는 500 에러 대신 동일한 화면을 보여준다).
        try {
            memberService.requestPasswordReset(dto.getEmail());
        } catch (Exception e) {
            log.warn("비밀번호 재설정 코드 발송 실패 (email={}): {}", dto.getEmail(), e.getMessage());
        }

        return "redirect:/members/password-reset/confirm?email=" + dto.getEmail();
    }

    // 비밀번호 재설정 확정 페이지 (인증 코드 + 새 비밀번호 입력)
    @GetMapping("/password-reset/confirm")
    public String passwordResetConfirmForm(@RequestParam("email") String email, Model model) {
        PasswordResetConfirmDto dto = new PasswordResetConfirmDto();
        dto.setEmail(email);
        model.addAttribute("passwordResetConfirmDto", dto);
        return "member/password-reset-confirm";
    }

    // 비밀번호 재설정 확정 처리
    @PostMapping("/password-reset/confirm")
    public String passwordResetConfirm(@Valid @ModelAttribute("passwordResetConfirmDto") PasswordResetConfirmDto dto,
                                       BindingResult bindingResult,
                                       HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            return "member/password-reset-confirm";
        }

        if (!dto.isPasswordMatched()) {
            bindingResult.rejectValue("newPasswordConfirm", "passwordMismatch", "비밀번호가 일치하지 않습니다.");
            return "member/password-reset-confirm";
        }

        try {
            memberService.confirmPasswordReset(dto.getEmail(), dto.getCode(), dto.getNewPassword());
        } catch (IllegalArgumentException e) {
            bindingResult.rejectValue("code", "resetFailed", e.getMessage());
            return "member/password-reset-confirm";
        }

        // 비밀번호 재설정 성공 시 현재 세션(있다면)을 무효화해, 재설정 이후 반드시 새 비밀번호로 다시 로그인하게 한다.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        return "redirect:/members/login?reset";
    }

    // 마이페이지 조회
    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal Object principal, Model model) {
        String email = SecurityUtils.requireEmail(principal);
        
        MemberProfileDto profile = memberService.getProfile(email);
        model.addAttribute("profile", profile);
        
        // 내가 쓴 글 / 작성한 댓글 목록 모델에 추가
        model.addAttribute("myPosts", postRepository.findByWriterEmailOrderByIdDesc(email));
        model.addAttribute("myComments", commentRepository.findByWriterEmailOrderByIdDesc(email));

        // 좋아요 및 북마크 목록 모델에 추가 (이메일 기준 조회 메서드 활용)
        model.addAttribute("myPostLikes", postLikeRepository.findByMemberEmailOrderByIdDesc(email));
        model.addAttribute("myScheduleLikes", scheduleLikeRepository.findByMemberEmailOrderByIdDesc(email));
        model.addAttribute("myBookmarks", scheduleBookmarkRepository.findByMemberEmailOrderByCreatedAtDesc(email));

        // 여행 취향 태그 (추천 기능용)
        model.addAttribute("tags", ScheduleTag.values());
        model.addAttribute("preferredTags", memberService.getPreferredTags(email));

        // 완료된 여행 (여행 일지 링크)
        model.addAttribute("completedTrips", schedulePortfolioService.getMyCompletedTrips(email));

        return "member/mypage";
    }

    // 프로필 수정 처리
    @PostMapping("/mypage")
    public String updateProfile(@AuthenticationPrincipal Object principal,
                                @ModelAttribute MemberProfileDto dto) {
        String email = SecurityUtils.requireEmail(principal);
        memberService.updateProfile(email, dto);
        return "redirect:/members/mypage?success";
    }

    // 여행 취향 태그 수정 처리
    @PostMapping("/mypage/tags")
    public String updatePreferredTags(@AuthenticationPrincipal Object principal,
                                      @RequestParam(name = "preferredTags", required = false) Set<ScheduleTag> preferredTags) {
        String email = SecurityUtils.requireEmail(principal);
        memberService.updatePreferredTags(email, preferredTags != null ? preferredTags : new HashSet<>());
        return "redirect:/members/mypage?success";
    }

    // 마이페이지 비밀번호 변경 처리
    @PostMapping("/mypage/password")
    public String changePassword(@AuthenticationPrincipal Object principal,
                                 @Valid @ModelAttribute("passwordChangeDto") PasswordChangeDto dto,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes,
                                 HttpServletRequest request) {
        String email = SecurityUtils.requireEmail(principal);

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("passwordError",
                    bindingResult.getFieldError().getDefaultMessage());
            return "redirect:/members/mypage";
        }

        if (!dto.isPasswordMatched()) {
            redirectAttributes.addFlashAttribute("passwordError", "새 비밀번호가 일치하지 않습니다.");
            return "redirect:/members/mypage";
        }

        try {
            memberService.changePassword(email, dto.getCurrentPassword(), dto.getNewPassword());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("passwordError", e.getMessage());
            return "redirect:/members/mypage";
        }

        // 비밀번호 변경 성공 시 현재 세션을 무효화해 즉시 로그아웃시키고, 새 비밀번호로 다시 로그인하게 한다.
        request.getSession().invalidate();

        return "redirect:/members/login?passwordChanged";
    }
}