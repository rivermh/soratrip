package com.rivermh.soratrip.domain.post.dto;

import com.rivermh.soratrip.domain.post.entity.Category;
import com.rivermh.soratrip.domain.post.entity.Post;
import com.rivermh.soratrip.domain.post.entity.Region;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
public class PostResponseDto {

	private Long id;
	private String title;
	private String content;
	private Region region;
	private Category category;
	private LocalDate travelStartDate;
	private LocalDate travelEndDate;
	private Integer recruitmentCount;
	private int viewCount;
	private String writerNickname;
	private String writerEmail;
	private LocalDateTime createdAt;

	// 위치정보
	private String placeName;
	private String placeAddress;
	private Double latitude;
	private Double longitude;

	// 💡 좋아요 관련 정보 추가
	private int likeCount;
	private boolean liked;

	// 기본 생성자 (Post + likeCount + liked)
	public PostResponseDto(Post post, int likeCount, boolean liked) {
		this.id = post.getId();
		this.title = post.getTitle();
		this.content = post.getContent();
		this.region = post.getRegion();
		this.category = post.getCategory();
		this.travelStartDate = post.getTravelStartDate();
		this.travelEndDate = post.getTravelEndDate();
		this.recruitmentCount = post.getRecruitmentCount();
		this.viewCount = post.getViewCount();
		this.writerNickname = post.getWriter().getNickname();
		this.writerEmail = post.getWriter().getEmail();
		this.createdAt = post.getCreatedAt();

		this.placeName = post.getPlaceName();
		this.placeAddress = post.getPlaceAddress();
		this.latitude = post.getLatitude();
		this.longitude = post.getLongitude();

		this.likeCount = likeCount;
		this.liked = liked;
	}

	// 좋아요 정보가 필요 없는 기존 호출 대응용 생성자
	public PostResponseDto(Post post) {
		this(post, 0, false);
	}
}