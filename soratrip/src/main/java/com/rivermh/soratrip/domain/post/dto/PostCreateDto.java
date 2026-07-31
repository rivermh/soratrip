package com.rivermh.soratrip.domain.post.dto;

import com.rivermh.soratrip.domain.post.entity.Category;
import com.rivermh.soratrip.domain.post.entity.Region;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class PostCreateDto {

    private String title;
    private String content;
    private Region region;
    private Category category;
    
    private LocalDate travelStartDate;
    private LocalDate travelEndDate;
    private Integer recruitmentCount;
    
    private String placeName;     // 장소명
    private String placeAddress;  // 주소
    private Double latitude;      // 위도
    private Double longitude;     // 경도
}