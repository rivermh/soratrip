package com.rivermh.soratrip.domain.member.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rivermh.soratrip.domain.member.dto.EmailSendRequestDto;
import com.rivermh.soratrip.domain.member.dto.EmailVerifyRequestDto;
import com.rivermh.soratrip.domain.member.service.MailService;
import com.rivermh.soratrip.domain.member.service.MemberService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;
    private final MessageSource messageSource;
    private final MailService mailService;

    /**
     * 이메일 중복 확인 API (GET 방식)
     * GET /api/members/check-email?email=xxx
     */
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestParam("email") String email) {
        Map<String, Object> response = new HashMap<>();
        var locale = LocaleContextHolder.getLocale();

        // 이메일 필수 검증
        if (email == null || email.trim().isEmpty()) {
            response.put("available", false);
            response.put("message", messageSource.getMessage("member.check_email.blank", null, locale));
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // memberService에서 이메일 중복 확인
            boolean isAvailable = memberService.isEmailAvailable(email.trim());
            response.put("available", isAvailable);

            if (isAvailable) {
                response.put("message", messageSource.getMessage("member.check_email.available", null, locale));
            } else {
                response.put("message", messageSource.getMessage("member.check_email.duplicate", null, locale));
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("available", false);
            response.put("message", messageSource.getMessage("member.check_email.error", null, locale));
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 이메일 인증 코드 발송 API
     * POST /api/members/email-send
     */
    @PostMapping("/email-send")
    public ResponseEntity<Map<String, Object>> sendEmailCode(@Valid @RequestBody EmailSendRequestDto request) {
        Map<String, Object> response = new HashMap<>();

        // 가입을 위한 이메일 발송 시 중복 검사
        if (!memberService.isEmailAvailable(request.getEmail())) {
            response.put("success", false);
            response.put("message", "이미 가입된 이메일입니다.");
            return ResponseEntity.badRequest().body(response);
        }

        mailService.sendVerificationCode(request.getEmail());
        response.put("success", true);
        response.put("message", "인증 코드가 이메일로 발송되었습니다.");
        return ResponseEntity.ok(response);
    }

    /**
     * 이메일 인증 코드 검증 API
     * POST /api/members/email-verify
     */
    @PostMapping("/email-verify")
    public ResponseEntity<Map<String, Object>> verifyEmailCode(@Valid @RequestBody EmailVerifyRequestDto request) {
        Map<String, Object> response = new HashMap<>();

        boolean isVerified = mailService.verifyCode(request.getEmail(), request.getCode());
        if (isVerified) {
            response.put("success", true);
            response.put("message", "이메일 인증이 완료되었습니다.");
            return ResponseEntity.ok(response);
        } else {
            response.put("success", false);
            response.put("message", "인증 코드가 일치하지 않거나 만료되었습니다.");
            return ResponseEntity.badRequest().body(response);
        }
    }
}