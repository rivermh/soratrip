package com.rivermh.soratrip.domain.schedule.dto;

import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
public class AiScheduleRequest {
    private Region region;        // 여행 지역 (TOKYO, OSAKA 등)
    @Min(value = 1, message = "{schedule.ai.validation.days_min}")
    @Max(value = 7, message = "{schedule.ai.validation.days_max}")
    private int daysCount;     // 여행 일수 (예: 3일차 = 3)
    @NotNull(message = "{schedule.ai.validation.start_date_required}")
    @FutureOrPresent(message = "{schedule.ai.validation.start_date_past}")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;   // 여행 시작일 (<input type="date">는 ISO 형식만 인식하므로, 로케일에 따라 값이 깨지지 않도록 패턴을 고정)
    private String companionType;  // 동행자 (혼자, 부모님, 아이 등)
    private Set<ScheduleTag> tags; // 이동 편의 및 취향 태그
    private String extraPrompt;    // 추가 요구사항 (예: 하루 1만보 이하, 대형 캐리어 이동)
}