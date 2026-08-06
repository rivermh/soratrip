package com.rivermh.soratrip.domain.like.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LikeToggleResponse {
    private boolean liked;    // true: 좋아요 추가됨 / false: 좋아요 취소됨
    private int likeCount;    // 변경 후 총 좋아요 수
}