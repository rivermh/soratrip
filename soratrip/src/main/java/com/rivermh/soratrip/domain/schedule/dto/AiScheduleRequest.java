package com.rivermh.soratrip.domain.schedule.dto;

import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class AiScheduleRequest {
    private Region region;        // 여행 지역 (TOKYO, OSAKA 등)
    private Integer daysCount;     // 여행 일수 (예: 3일차 = 3)
    private String companionType;  // 동행자 (혼자, 부모님, 아이 등)
    private Set<ScheduleTag> tags; // 이동 편의 및 취향 태그
    private String extraPrompt;    // 추가 요구사항 (예: 하루 1만보 이하, 대형 캐리어 이동)
}