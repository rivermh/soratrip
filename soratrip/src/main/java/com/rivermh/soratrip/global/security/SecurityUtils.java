package com.rivermh.soratrip.global.security;

import java.util.Collection;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

// 일반 로그인(UserDetails)과 소셜 로그인(OAuth2User)에서 공통으로 로그인 이메일을 추출하는 유틸리티
public final class SecurityUtils {

    private SecurityUtils() {
    }

    // principal에서 이메일 추출. 인증 정보를 알 수 없으면 null 반환
    public static String extractEmail(Object principal) {
        if (principal instanceof OAuth2User oAuth2User) {
            return oAuth2User.getAttribute("email");
        } else if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return null;
    }

    // principal에서 이메일을 추출하되, 없으면 예외를 던짐
    public static String requireEmail(Object principal) {
        String email = extractEmail(principal);
        if (email == null) {
            throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
        }
        return email;
    }

    // Authentication에서 이메일을 추출하되, 없으면 예외를 던짐 (REST API용)
    public static String requireEmail(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        String email = extractEmail(authentication.getPrincipal());
        if (email == null) {
            throw new IllegalArgumentException("인증 정보를 찾을 수 없습니다.");
        }
        return email;
    }

    // principal이 ROLE_ADMIN 권한을 가지고 있는지 확인 (일반 로그인/소셜 로그인 공통)
    public static boolean isAdmin(Object principal) {
        Collection<? extends GrantedAuthority> authorities = null;
        if (principal instanceof OAuth2User oAuth2User) {
            authorities = oAuth2User.getAuthorities();
        } else if (principal instanceof UserDetails userDetails) {
            authorities = userDetails.getAuthorities();
        }
        return authorities != null && authorities.stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}
