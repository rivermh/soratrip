package com.rivermh.soratrip.domain.review.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReviewUpsertDto {
    private String content;
    private Integer rating;

    // 이동 편의 체크박스 항목
    private Boolean hasElevator = false;
    private Boolean isFlatPath = false;
    private Boolean hasLuggageStorage = false;
}