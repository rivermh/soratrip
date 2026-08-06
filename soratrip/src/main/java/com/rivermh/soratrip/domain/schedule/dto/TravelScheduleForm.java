package com.rivermh.soratrip.domain.schedule.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.rivermh.soratrip.domain.post.entity.Region;
import com.rivermh.soratrip.domain.schedule.entity.ScheduleTag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TravelScheduleForm {

    @NotBlank(message = "일정 제목을 입력해 주세요.")
    private String title;

    @NotNull(message = "여행 지역을 선택해 주세요.")
    private Region region;

    @NotNull(message = "시작일을 입력해 주세요.")
    private LocalDate startDate;

    @NotNull(message = "종료일을 입력해 주세요.")
    private LocalDate endDate;

    // 일정 관련 선택 태그 (이동 편의 태그 포함)
    private List<ScheduleTag> tags = new ArrayList<>();

    private List<ScheduleDayForm> days = new ArrayList<>();
}