package com.rivermh.soratrip.domain.schedule.dto;

import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class AiScheduleRequest {
    private Region region;        // 여행 지역 (TOKYO, OSAKA 등)
    @Min(value = 1, message = "여행 기간은 최소 1일 이상이어야 합니다.")
    @Max(value = 7, message = "AI 일정 생성은 최대 7일까지 가능합니다.")
    private int daysCount;     // 여행 일수 (예: 3일차 = 3)
    private String companionType;  // 동행자 (혼자, 부모님, 아이 등)
    private Set<ScheduleTag> tags; // 이동 편의 및 취향 태그
    private String extraPrompt;    // 추가 요구사항 (예: 하루 1만보 이하, 대형 캐리어 이동)
}