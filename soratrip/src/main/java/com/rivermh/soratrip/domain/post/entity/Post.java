package com.rivermh.soratrip.domain.post.entity;

import com.rivermh.soratrip.domain.member.entity.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Region region;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Category category;

	// 동행 관련 필드 (선택적)
	private LocalDate travelStartDate; // 여행 시작일
	private LocalDate travelEndDate; // 여행 종료일
	private Integer recruitmentCount; // 모집 인원 수

	private int viewCount = 0; // 조회수

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "member_id", nullable = false)
	private Member writer;

	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	@PrePersist
	public void prePersist() {
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	public void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

	@Builder
	public Post(String title, String content, Region region, Category category, LocalDate travelStartDate,
			LocalDate travelEndDate, Integer recruitmentCount, Member writer) {
		this.title = title;
		this.content = content;
		this.region = region;
		this.category = category;
		this.travelStartDate = travelStartDate;
		this.travelEndDate = travelEndDate;
		this.recruitmentCount = recruitmentCount;
		this.writer = writer;
	}

	// 조회수 증가
	public void increaseViewCount() {
		this.viewCount++;
	}

	// 게시글 수정
	public void update(String title, String content, Region region, Category category, LocalDate travelStartDate,
			LocalDate travelEndDate, Integer recruitmentCount) {
		this.title = title;
		this.content = content;
		this.region = region;
		this.category = category;
		this.travelStartDate = travelStartDate;
		this.travelEndDate = travelEndDate;
		this.recruitmentCount = recruitmentCount;
	}

	// 위치 관련 필드 추가
	private String placeName; // 장소명 (예: 도쿄 타워, 강남역)
	private String placeAddress; // 주소
	private Double latitude; // 위도
	private Double longitude; // 경도

	// 생성자 및 수정 메서드 업데이트
	public void updateLocation(String placeName, String placeAddress, Double latitude, Double longitude) {
		this.placeName = placeName;
		this.placeAddress = placeAddress;
		this.latitude = latitude;
		this.longitude = longitude;
	}
}