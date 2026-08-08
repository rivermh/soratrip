package com.rivermh.soratrip.domain.member.controller;

import java.util.HashSet;
import java.util.Set;

import com.rivermh.soratrip.domain.bookmark.repository.ScheduleBookmarkRepository;
import com.rivermh.soratrip.domain.comment.repository.CommentRepository;
import com.rivermh.soratrip.domain.like.repository.PostLikeRepository;
import com.rivermh.soratrip.domain.like.repository.ScheduleLikeRepository;
import com.rivermh.soratrip.domain.member.dto.MemberJoinDto;
import com.rivermh.soratrip.domain.member.dto.MemberProfileDto;
import com.rivermh.soratrip.domain.member.service.MemberService;
import com.rivermh.soratrip.domain.post.repository.PostRepository;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.domain.schedule.service.SchedulePortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    public String joinForm(Model model) {
        model.addAttribute("memberJoinDto", new MemberJoinDto());
        return "member/join";
    }

    // 회원가입 처리
    @PostMapping("/join")
    public String join(@ModelAttribute MemberJoinDto dto) {
        memberService.join(dto);
        return "redirect:/members/login"; // 가입 완료 후 로그인 페이지로 이동
    }

    // 로그인 페이지 요청
    @GetMapping("/login")
    public String loginForm() {
        return "member/login";
    }

    // 마이페이지 조회
    @GetMapping("/mypage")
    public String myPage(@AuthenticationPrincipal Object principal, Model model) {
        String email = extractEmail(principal);
        
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
        String email = extractEmail(principal);
        memberService.updateProfile(email, dto);
        return "redirect:/members/mypage?success";
    }

    // 여행 취향 태그 수정 처리
    @PostMapping("/mypage/tags")
    public String updatePreferredTags(@AuthenticationPrincipal Object principal,
                                      @RequestParam(name = "preferredTags", required = false) Set<ScheduleTag> preferredTags) {
        String email = extractEmail(principal);
        memberService.updatePreferredTags(email, preferredTags != null ? preferredTags : new HashSet<>());
        return "redirect:/members/mypage?success";
    }

    // 로그인한 유저의 이메일을 뽑아내는 헬퍼 메서드 (일반로그인/소셜로그인 공통 대응)
    private String extractEmail(Object principal) {
        if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }
        throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
    }
}