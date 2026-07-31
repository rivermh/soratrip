package com.rivermh.soratrip.domain.member.service;

import java.util.Set;

import com.rivermh.soratrip.domain.member.dto.MemberJoinDto;
import com.rivermh.soratrip.domain.member.dto.MemberProfileDto;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.entity.Role;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long join(MemberJoinDto dto) {
        // 이메일 중복 검시
        if (memberRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }       
        Member member = Member.builder()
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword())) // 비밀번호 암호화
                .nickname(dto.getNickname())
                .role(Role.USER)
                .provider("local")
                .build();

        return memberRepository.save(member).getId();
    }
    
 // 프로필 조회
    @Transactional(readOnly = true)
    public MemberProfileDto getProfile(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        return new MemberProfileDto(
                member.getEmail(),
                member.getNickname(),
                member.getProfileImage(),
                member.getBio(),
                member.getLanguageLevel(),
                member.getNationality()
        );
    }

    // 프로필 수정
    @Transactional
    public void updateProfile(String email, MemberProfileDto dto) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        member.updateProfile(dto.getNickname(), dto.getBio(), dto.getLanguageLevel(), dto.getNationality());
    }

    // 여행 취향 태그 수정 (추천 기능용)
    @Transactional
    public void updatePreferredTags(String email, Set<ScheduleTag> preferredTags) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        member.updatePreferredTags(preferredTags);
    }

    // 여행 취향 태그 조회
    public Set<ScheduleTag> getPreferredTags(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        return member.getPreferredTags();
    }
}