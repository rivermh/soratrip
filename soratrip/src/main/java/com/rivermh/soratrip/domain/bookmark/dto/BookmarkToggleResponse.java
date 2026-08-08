package com.rivermh.soratrip.domain.bookmark.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BookmarkToggleResponse {
    private boolean bookmarked; // 북마크 상태 (true: 저장됨, false: 취소됨)
    private int bookmarkCount;  // 최신 총 북마크 수
}