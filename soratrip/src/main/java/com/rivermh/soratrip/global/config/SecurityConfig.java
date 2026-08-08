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
                .requestMatchers("/", "/members/join", "/members/login", "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error", "/uploads/**").permitAll()
                // WebSocket 핸드셰이크 경로 허용 (인증된 사용자만 연결 가능하도록 authenticated() 처리 또는 permitAll())
                .requestMatchers("/ws/chat/**").authenticated() 
                // 채팅 관련 REST API 경로 인증 설정
                .requestMatchers("/api/chat/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/members/login")
                .loginProcessingUrl("/members/login")
                .usernameParameter("email")
                .defaultSuccessUrl("/", true)
                .failureUrl("/members/login?error")
                .permitAll()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/members/login")
                .defaultSuccessUrl("/", true)
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
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