package com.rivermh.soratrip.domain.schedule.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ScheduleOrderUpdateRequest {

    private List<ItemOrderDto> items;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class ItemOrderDto {
        private Long itemId;
        private Integer visitOrder;
    }
}