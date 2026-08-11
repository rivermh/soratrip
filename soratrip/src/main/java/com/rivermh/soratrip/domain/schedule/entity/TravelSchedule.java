package com.rivermh.soratrip.domain.schedule.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Member, Region 패키지 경로에 맞게 확인해 주세요
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.post.entity.Region;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.LinkedHashSet;
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TravelSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member; // 작성자

    @Column(nullable = false)
    private String title; // 예: "3박 4일 도쿄 먹방 여행"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Region region; // 여행 지역 (TOKYO, OSAKA 등)

    private LocalDate startDate;
    private LocalDate endDate;

    // 여행 예산 (원화 기준, 선택 입력)
    private BigDecimal budgetKrw;

    @Builder.Default
    @Column(nullable = false)
    private boolean isPublic = false;

    // 로그인 없이 볼 수 있는 공유 링크용 토큰 (미발급 시 null, 값이 있으면 /shared/{token}으로 열람 가능)
    @Column(unique = true)
    private String shareToken;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "schedule_tags", joinColumns = @JoinColumn(name = "travel_schedule_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", length = 50, nullable = false)
    @Builder.Default
    private Set<ScheduleTag> tags = new HashSet<>();

    @OneToMany(mappedBy = "travelSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    @Builder.Default
    private Set<ScheduleDay> days = new LinkedHashSet<>();

    @OneToMany(mappedBy = "travelSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChecklistItem> checklistItems = new ArrayList<>();

    @OneToMany(mappedBy = "travelSchedule", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TravelBooking> bookings = new ArrayList<>();

    // 공개 여부 변경
    public void updateVisibility(boolean isPublic) {
        this.isPublic = isPublic;
    }

    // 공유 링크 발급 (기존 토큰이 있으면 그대로 재사용)
    public String issueShareToken() {
        if (this.shareToken == null) {
            this.shareToken = java.util.UUID.randomUUID().toString().replace("-", "");
        }
        return this.shareToken;
    }

    // 공유 링크 폐기
    public void revokeShareToken() {
        this.shareToken = null;
    }

    // 이메일 기준 소유자 여부 확인 (email이 null이어도 안전하게 false 반환)
    public boolean isOwnedBy(String email) {
        return member != null && member.getEmail().equals(email);
    }

    // 태그 전체 교체
    public void updateTags(Set<ScheduleTag> tags) {
        this.tags.clear();
        this.tags.addAll(tags);
    }

    // 예산 변경 (null 허용 = 예산 미설정)
    public void updateBudget(BigDecimal budgetKrw) {
        this.budgetKrw = budgetKrw;
    }
    
    // 좋아요
    @Builder.Default
    private int likeCount = 0;

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }
}