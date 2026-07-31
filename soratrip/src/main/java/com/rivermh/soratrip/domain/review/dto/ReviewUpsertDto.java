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
}
