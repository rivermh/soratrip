package com.rivermh.soratrip.domain.schedule.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleDayForm {
    private Integer dayNumber;
    private LocalDate visitDate;
    private List<ScheduleItemForm> items = new ArrayList<>();
}