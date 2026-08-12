package com.rivermh.soratrip.global.security.handler;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 구글 로그인 실패 처리. CustomOAuth2UserService가 정지 계정에 대해 던지는
 * "suspended_account" 에러코드만 별도 안내로 리다이렉트하고, 나머지는 기존 기본 동작과 동일하게 처리한다.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        if (exception instanceof OAuth2AuthenticationException oAuth2Exception
                && "suspended_account".equals(oAuth2Exception.getError().getErrorCode())) {
            response.sendRedirect(request.getContextPath() + "/members/login?suspended");
            return;
        }

        response.sendRedirect(request.getContextPath() + "/members/login?error");
    }
}
