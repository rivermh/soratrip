package com.rivermh.soratrip.domain.schedule.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ClaudeScheduleResponse {
    private String title;
    private List<DayDto> days;

    @Getter @Setter @NoArgsConstructor
    public static class DayDto {
        private Integer dayNumber;
        private List<ItemDto> items;
    }

    @Getter @Setter @NoArgsConstructor
    public static class ItemDto {
        private String placeName;
        private String placeAddress;
        private Double latitude;
        private Double longitude;
        private String visitTime; // "10:00" 형식
        private Integer visitOrder;
        private String memo;
        private String recommendReason; // AI가 이 장소를 고른 이유 (태그/동행자/추가요청과 연결된 한 줄 근거)
    }
}