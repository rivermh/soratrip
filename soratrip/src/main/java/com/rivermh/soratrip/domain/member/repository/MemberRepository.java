package com.rivermh.soratrip.domain.member.repository;


import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rivermh.soratrip.domain.member.entity.Member;


public interface MemberRepository extends JpaRepository<Member, Long> {

	// 이메일로 회원 조회 (소셜 로그인 시 기존 회원 여부 확인용)
	Optional<Member> findByEmail(String email);

	// 소셜 타입과 소셜 ID로 회원 조회
	Optional<Member> findByProviderAndProviderId(String provider, String providerId);

	// 관리자 통계용: 특정 시점 이후 가입한 회원 수
	long countByCreatedAtAfter(LocalDateTime dateTime);

	// 관리자 회원 관리: 이메일/닉네임 검색 + 페이징
	@Query(value = "SELECT m FROM Member m WHERE (:keyword IS NULL OR LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
			"OR LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))",
			countQuery = "SELECT COUNT(m) FROM Member m WHERE (:keyword IS NULL OR LOWER(m.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
			"OR LOWER(m.nickname) LIKE LOWER(CONCAT('%', :keyword, '%')))")
	Page<Member> searchMembers(@Param("keyword") String keyword, Pageable pageable);

}
