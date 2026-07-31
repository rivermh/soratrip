package com.rivermh.soratrip.domain.member.repository;


import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rivermh.soratrip.domain.member.entity.Member;


public interface MemberRepository extends JpaRepository<Member, Long> {

	// 이메일로 회원 조회 (소셜 로그인 시 기존 회원 여부 확인용)
	Optional<Member> findByEmail(String email);

	// 소셜 타입과 소셜 ID로 회원 조회
	Optional<Member> findByProviderAndProviderId(String provider, String providerId);

}
