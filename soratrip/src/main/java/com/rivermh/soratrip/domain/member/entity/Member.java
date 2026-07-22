package com.rivermh.soratrip.domain.member.entity;

import com.rivermh.soratrip.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "members")
public class Member extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "member_id")
	private Long id;
	
	@Column(nullable = false, unique = true)
	private String email;
	
	private String password; // 일반 회원가입용 (소셜 가입자는 null 가능)
	
	@Column(nullable = false)
	private String nickname;
	
	private String profileImage;
	
	@Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role; // USER, ADMIN 등

    private String provider; // kakao, line 등 (소셜 로그인용)
    private String providerId;

    @Builder
    public Member(String email, String password, String nickname, String profileImage, Role role, String provider, String providerId) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
    }
	
}
