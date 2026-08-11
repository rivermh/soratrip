package com.rivermh.soratrip.domain.post.entity;

import java.time.LocalDateTime;

import com.rivermh.soratrip.domain.member.entity.Member;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 동행구하기 게시글에 대한 참가 신청
@Entity
@Table(name = "post_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Member applicant;

    private String message; // 신청자가 남긴 짧은 소개/인사말 (선택)

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public PostApplication(Post post, Member applicant, String message) {
        this.post = post;
        this.applicant = applicant;
        this.message = message;
        this.status = ApplicationStatus.PENDING;
    }

    public void accept() {
        this.status = ApplicationStatus.ACCEPTED;
    }

    public void reject() {
        this.status = ApplicationStatus.REJECTED;
    }
}
