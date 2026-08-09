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

        // 로그인 5회 실패로 잠긴 계정이면 accountNonLocked=false를 반환해
        // DaoAuthenticationProvider가 비밀번호 비교 전에 LockedException을 던지게 한다.
        boolean locked = authRedisService.isLocked(email);

        return User.builder()
                .username(member.getEmail())
                .password(member.getPassword())
                .authorities(member.getRole().getKey())
                .accountLocked(locked)
                .build();
    }
}