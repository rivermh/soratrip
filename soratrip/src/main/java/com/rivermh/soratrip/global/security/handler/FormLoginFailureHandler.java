package com.rivermh.soratrip.global.security.handler;

import java.io.IOException;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.rivermh.soratrip.domain.member.service.AuthRedisService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 일반 로그인 실패 처리. 이미 잠긴 계정은 실패 카운트를 더 늘리지 않고(잠금 시간 연장 방지)
 * 별도 안내로 리다이렉트하며, 그 외(비밀번호 불일치 등)는 실패 카운트를 증가시켜 5회째 잠금을 건다.
 */
@Component
@RequiredArgsConstructor
public class FormLoginFailureHandler implements AuthenticationFailureHandler {

    private final AuthRedisService authRedisService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        String email = request.getParameter("email");

        if (exception instanceof LockedException) {
            response.sendRedirect(request.getContextPath() + "/members/login?locked");
            return;
        }

        if (exception instanceof DisabledException) {
            response.sendRedirect(request.getContextPath() + "/members/login?suspended");
            return;
        }

        if (email != null && !email.isBlank()) {
            authRedisService.recordLoginFailure(email.trim());
        }
        response.sendRedirect(request.getContextPath() + "/members/login?error");
    }
}
