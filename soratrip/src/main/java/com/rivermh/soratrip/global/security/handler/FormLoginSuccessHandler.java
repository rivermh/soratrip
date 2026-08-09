package com.rivermh.soratrip.global.security.handler;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.rivermh.soratrip.domain.member.service.AuthRedisService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 일반(이메일/비밀번호) 로그인 성공 시 실패 카운트/잠금 상태를 초기화한다.
 * 세션 발급 자체는 Spring Security 기본 동작에 맡긴다.
 */
@Component
@RequiredArgsConstructor
public class FormLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthRedisService authRedisService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        authRedisService.clearLoginAttempts(authentication.getName());
        response.sendRedirect(request.getContextPath() + "/");
    }
}
