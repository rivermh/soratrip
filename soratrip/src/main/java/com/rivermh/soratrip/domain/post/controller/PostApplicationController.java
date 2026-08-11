package com.rivermh.soratrip.domain.post.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.rivermh.soratrip.domain.post.service.PostApplicationService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/posts/{postId}/applications")
@RequiredArgsConstructor
public class PostApplicationController {

    private final PostApplicationService postApplicationService;

    // 동행 신청
    @PostMapping
    public String apply(@PathVariable("postId") Long postId,
                        @RequestParam(name = "message", required = false) String message,
                        Principal principal) {
        postApplicationService.apply(postId, principal.getName(), message);
        return "redirect:/posts/" + postId;
    }

    // 신청 수락 (글쓴이 전용)
    @PostMapping("/{applicationId}/accept")
    public String accept(@PathVariable("postId") Long postId,
                         @PathVariable("applicationId") Long applicationId,
                         Principal principal) {
        postApplicationService.accept(applicationId, principal.getName());
        return "redirect:/posts/" + postId;
    }

    // 신청 거절 (글쓴이 전용)
    @PostMapping("/{applicationId}/reject")
    public String reject(@PathVariable("postId") Long postId,
                         @PathVariable("applicationId") Long applicationId,
                         Principal principal) {
        postApplicationService.reject(applicationId, principal.getName());
        return "redirect:/posts/" + postId;
    }
}
