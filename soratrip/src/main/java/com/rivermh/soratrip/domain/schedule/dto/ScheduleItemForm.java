package com.rivermh.soratrip.domain.schedule.dto;

import java.time.LocalTime;

import com.rivermh.soratrip.domain.schedule.entity.PlaceType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleItemForm {
    private String placeName;
    private String placeAddress;
    private Double latitude;
    private Double longitude;
    private LocalTime visitTime;
    private Integer visitOrder;
    private String memo;
    private PlaceType placeType;
}