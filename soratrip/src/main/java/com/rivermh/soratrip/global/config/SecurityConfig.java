package com.rivermh.soratrip.global.config;

import com.rivermh.soratrip.domain.member.service.CustomOAuth2UserService;
import com.rivermh.soratrip.global.security.handler.FormLoginFailureHandler;
import com.rivermh.soratrip.global.security.handler.FormLoginSuccessHandler;
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
	private final FormLoginSuccessHandler formLoginSuccessHandler;
	private final FormLoginFailureHandler formLoginFailureHandler;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				// 세션이 없어도 되는 SockJS 폴백 전송(xhr-streaming 등)과, 폼 CSRF 토큰 없이 fetch로 직접
				// 호출되는 이메일 인증 코드 발송/검증 API만 CSRF 예외 처리
				.csrf(csrf -> csrf
						.ignoringRequestMatchers("/ws/**", "/api/members/email-send", "/api/members/email-verify"))
				.authorizeHttpRequests(auth -> auth
						// "/error"는 인가 실패(403) 등으로 인한 컨테이너 에러 디스패치도 통과해야 하므로 permitAll 유지
						.requestMatchers("/", "/members/join", "/members/signup-success", "/members/login",
								"/members/password-reset", "/members/password-reset/confirm",
								"/api/members/**", "/css/**", "/js/**",
								"/images/**", "/favicon.ico", "/error", "/uploads/**")
						.permitAll()
						// WebSocket 핸드셰이크 경로 허용 (인증된 사용자만 연결 가능하도록 authenticated() 처리)
						.requestMatchers("/ws/chat/**").authenticated()
						// 채팅 관련 REST API 경로 인증 설정
						.requestMatchers("/api/chat/**").authenticated().anyRequest().authenticated())
				.formLogin(form -> form.loginPage("/members/login").loginProcessingUrl("/members/login")
						.usernameParameter("email")
						.successHandler(formLoginSuccessHandler)
						.failureHandler(formLoginFailureHandler)
						.permitAll())
				.oauth2Login(oauth2 -> oauth2.loginPage("/members/login")
						.defaultSuccessUrl("/", true)
						.userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService)))
				.logout(logout -> logout.logoutUrl("/members/logout").logoutSuccessUrl("/")
						// 예전 JWT 실험에서 발급된 쿠키가 브라우저에 남아있을 수 있어 로그아웃 시 함께 정리
						.deleteCookies("access_token", "refresh_token"));

		return http.build();
	}
}
