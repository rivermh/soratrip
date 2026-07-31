package com.rivermh.soratrip.global.config;

import com.rivermh.soratrip.domain.member.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/members/join", "/members/login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/uploads/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/members/login")
                .loginProcessingUrl("/members/login")
                .usernameParameter("email")
                .defaultSuccessUrl("/", true)
                .failureUrl("/members/login?error") // 로그인 실패 시 ?error 파라미터 전달
                .permitAll()
            )
            // OAuth2 소셜 로그인 설정 추가
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/members/login") // 구글 로그인 요청 시에도 커스텀 로그인 페이지 활용
                .defaultSuccessUrl("/", true)
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService) // 구글 유저 정보를 처리할 서비스 등록
                )
            )
            .logout(logout -> logout
                .logoutUrl("/members/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
            );

        return http.build();
    }
}