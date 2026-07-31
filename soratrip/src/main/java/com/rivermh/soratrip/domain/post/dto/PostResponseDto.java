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

	public PostResponseDto(Post post) {
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

		// 위치 데이터 바인딩
		this.placeName = post.getPlaceName();
		this.placeAddress = post.getPlaceAddress();
		this.latitude = post.getLatitude();
		this.longitude = post.getLongitude();
	}
}