package com.rivermh.soratrip.domain.comment.controller;

import com.rivermh.soratrip.domain.comment.dto.CommentRequestDto;
import com.rivermh.soratrip.domain.comment.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
   

    // 댓글 등록
    @PostMapping
    public String createComment(@PathVariable(name = "postId") Long postId,
                                @ModelAttribute CommentRequestDto dto,
                                @AuthenticationPrincipal Object principal) {
        String email = extractEmail(principal);
        commentService.createComment(postId, dto, email);
        return "redirect:/posts/" + postId;
    }

    // 댓글 삭제
    @PostMapping("/{commentId}/delete")
    public String deleteComment(@PathVariable(name = "postId") Long postId,
                                @PathVariable(name = "commentId") Long commentId,
                                @AuthenticationPrincipal Object principal) {
        String email = extractEmail(principal);
        commentService.deleteComment(commentId, email);
        return "redirect:/posts/" + postId;
    }

    private String extractEmail(Object principal) {
        if (principal instanceof org.springframework.security.oauth2.core.user.OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return userDetails.getUsername();
        }
        throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
    }
    
    
}