package com.rivermh.soratrip.domain.comment.controller;

import com.rivermh.soratrip.domain.comment.dto.CommentRequestDto;
import com.rivermh.soratrip.domain.comment.service.CommentService;
import com.rivermh.soratrip.global.security.SecurityUtils;
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
        String email = SecurityUtils.requireEmail(principal);
        commentService.createComment(postId, dto, email);
        return "redirect:/posts/" + postId;
    }

    // 댓글 수정
    @PostMapping("/{commentId}/edit")
    public String editComment(@PathVariable(name = "postId") Long postId,
                              @PathVariable(name = "commentId") Long commentId,
                              @ModelAttribute CommentRequestDto dto,
                              @AuthenticationPrincipal Object principal) {
        String email = SecurityUtils.requireEmail(principal);
        commentService.updateComment(commentId, dto, email);
        return "redirect:/posts/" + postId;
    }

    // 댓글 삭제
    @PostMapping("/{commentId}/delete")
    public String deleteComment(@PathVariable(name = "postId") Long postId,
                                @PathVariable(name = "commentId") Long commentId,
                                @AuthenticationPrincipal Object principal) {
        String email = SecurityUtils.requireEmail(principal);
        commentService.deleteComment(commentId, email);
        return "redirect:/posts/" + postId;
    }
}