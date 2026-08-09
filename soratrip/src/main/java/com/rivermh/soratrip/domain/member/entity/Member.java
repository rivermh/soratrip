package com.rivermh.soratrip.domain.member.entity;

import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import com.rivermh.soratrip.global.entity.BaseTimeEntity;
import com.rivermh.soratrip.domain.schedule.entity.TravelSchedule;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
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
    
    // --- 회원 프로필 정보 ---
    @Column(length = 500)
    private String bio; // 간단 자기소개
    
    private String languageLevel;

    private String nationality;

    // --- 여행 취향 태그 (추천 기능용) ---
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_preferred_tags", joinColumns = @JoinColumn(name = "member_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false)
    private Set<ScheduleTag> preferredTags = new HashSet<>();

 // --- 회원과 여행 일정 양방향 연관관계 ---
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TravelSchedule> schedules = new ArrayList<>();

    @Builder
    public Member(String email, String password, String nickname,
            String profileImage, Role role, String provider, String providerId,
            String bio, String languageLevel, String nationality) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.role = role;
        this.provider = provider;
        this.providerId = providerId;
        this.bio = bio;
        this.languageLevel = languageLevel;
        this.nationality = nationality;
    }
    
    // 프로필 정보 수정 메서드
    public void updateProfile(String nickname, String bio, String languageLevel, String nationality) {
        this.nickname = nickname;
        this.bio = bio;
        this.languageLevel = languageLevel;
        this.nationality = nationality;
    }

    // 여행 취향 태그 전체 교체
    public void updatePreferredTags(Set<ScheduleTag> preferredTags) {
        this.preferredTags.clear();
        this.preferredTags.addAll(preferredTags);
    }

    // 비밀번호 변경 (인코딩된 값을 받는다 — 인코딩 책임은 서비스 계층)
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}