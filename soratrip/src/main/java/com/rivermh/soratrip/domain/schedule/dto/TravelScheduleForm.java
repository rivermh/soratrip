package com.rivermh.soratrip.domain.schedule.dto;

import java.math.BigDecimal;
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

    @NotBlank(message = "{schedule.validation.title_required}")
    private String title;

    @NotNull(message = "{schedule.validation.region_required}")
    private Region region;

    @NotNull(message = "{schedule.validation.start_date_required}")
    private LocalDate startDate;

    @NotNull(message = "{schedule.validation.end_date_required}")
    private LocalDate endDate;

    // 여행 예산 (원화 기준, 선택 입력)
    private BigDecimal budgetKrw;

    // 일정 관련 선택 태그 (이동 편의 태그 포함)
    private List<ScheduleTag> tags = new ArrayList<>();

    private List<ScheduleDayForm> days = new ArrayList<>();
}