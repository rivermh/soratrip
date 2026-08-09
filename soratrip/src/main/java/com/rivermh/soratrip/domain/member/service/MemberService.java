package com.rivermh.soratrip.domain.member.service;

import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rivermh.soratrip.domain.member.dto.MemberJoinDto;
import com.rivermh.soratrip.domain.member.dto.MemberProfileDto;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.entity.Role;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;

    /**
     * 회원가입
     * 
     * @param dto 회원가입 정보
     * @return 생성된 회원 ID
     */
    @Transactional
    public Long join(MemberJoinDto dto) {
        if (!dto.isPasswordMatched()) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        if (memberRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        // 이메일 선-인증 완료 여부 검증
        if (!mailService.isEmailVerified(dto.getEmail())) {
            throw new IllegalArgumentException("이메일 인증이 완료되지 않았습니다.");
        }

        Member member = Member.builder()
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .nickname(dto.getNickname())
                .role(Role.USER)
                .provider("local")
                .build();

        Long memberId = memberRepository.save(member).getId();

        // 회원가입 성공 후 Redis의 인증 상태 삭제
        mailService.clearVerificationStatus(dto.getEmail());

        return memberId;
    }

    /**
     * 이메일 사용 가능 여부 확인
     * 
     * @param email 확인할 이메일
     * @return 사용 가능하면 true, 중복이면 false
     */
    public boolean isEmailAvailable(String email) {
        return memberRepository.findByEmail(email).isEmpty();
    }

    /**
     * 프로필 조회
     * 
     * @param email 회원 이메일
     * @return 회원 프로필 정보
     */
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

    /**
     * 프로필 수정
     * 
     * @param email 회원 이메일
     * @param dto   수정할 프로필 정보
     */
    @Transactional
    public void updateProfile(String email, MemberProfileDto dto) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        member.updateProfile(
                dto.getNickname(),
                dto.getBio(),
                dto.getLanguageLevel(),
                dto.getNationality()
        );
    }

    /**
     * 여행 취향 태그 수정
     * 
     * @param email         회원 이메일
     * @param preferredTags 선택한 여행 태그들
     */
    @Transactional
    public void updatePreferredTags(String email, Set<ScheduleTag> preferredTags) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        member.updatePreferredTags(preferredTags);
    }

    /**
     * 여행 취향 태그 조회
     * 
     * @param email 회원 이메일
     * @return 회원이 선택한 여행 태그들
     */
    public Set<ScheduleTag> getPreferredTags(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        return member.getPreferredTags();
    }

    /**
     * 비밀번호 재설정 요청 (비로그인 상태) — 인증 코드 발송
     */
    public void requestPasswordReset(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (member.getPassword() == null) {
            throw new IllegalArgumentException("소셜 로그인으로 가입된 계정은 비밀번호 재설정을 이용할 수 없습니다.");
        }

        mailService.sendPasswordResetCode(email);
    }

    /**
     * 비밀번호 재설정 확정 (비로그인 상태) — 인증 코드 검증 후 비밀번호를 변경한다.
     * 현재 세션 무효화는 HttpServletRequest에 접근 가능한 컨트롤러 계층에서 처리한다.
     */
    @Transactional
    public void confirmPasswordReset(String email, String code, String newPassword) {
        if (mailService.isPasswordResetLocked(email)) {
            throw new IllegalArgumentException("인증 시도 횟수를 초과했습니다. 재설정을 처음부터 다시 요청해주세요.");
        }

        if (!mailService.verifyPasswordResetCode(email, code)) {
            throw new IllegalArgumentException("인증 코드가 일치하지 않거나 만료되었습니다.");
        }

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        member.updatePassword(passwordEncoder.encode(newPassword));
    }

    /**
     * 마이페이지 비밀번호 변경 (로그인 상태) — 현재 비밀번호를 확인한 뒤 변경한다.
     * 현재 세션 무효화는 HttpServletRequest에 접근 가능한 컨트롤러 계층에서 처리한다.
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        if (member.getPassword() == null) {
            throw new IllegalArgumentException("소셜 로그인으로 가입된 계정은 비밀번호를 변경할 수 없습니다.");
        }

        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        member.updatePassword(passwordEncoder.encode(newPassword));
    }
}