package com.rivermh.soratrip.domain.member.service;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;
    private final AuthRedisService authRedisService;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("해당 이메일을 가진 회원을 찾을 수 없습니다: " + email));

        // 소셜 로그인 전용 계정은 password가 없다. User.builder()에 null을 그대로 넘기면
        // Assert.isTrue가 IllegalArgumentException(비인증 예외)을 던져 500 에러로 새고
        // 일반 로그인 실패와 다른 응답이 돼 계정 존재 여부가 유출되므로,
        // 동일하게 UsernameNotFoundException으로 처리해 자격 증명 실패로 흡수시킨다.
        if (member.getPassword() == null) {
            throw new UsernameNotFoundException("해당 이메일을 가진 회원을 찾을 수 없습니다: " + email);
        }

        // 로그인 5회 실패로 잠긴 계정이면 accountNonLocked=false를 반환해
        // DaoAuthenticationProvider가 비밀번호 비교 전에 LockedException을 던지게 한다.
        boolean locked = authRedisService.isLocked(email);

        return User.builder()
                .username(member.getEmail())
                .password(member.getPassword())
                .authorities(member.getRole().getKey())
                .accountLocked(locked)
                .disabled(member.isSuspended())
                .build();
    }
}