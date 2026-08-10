package com.rivermh.soratrip.domain.schedule.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class DayWeatherSummaryDto {
    private Integer dayNumber;        // N일차
    private LocalDate visitDate;      // 방문 날짜
    private String representativePlace; // 해당 일자의 대표 장소명
    private Double temp;              // 대표 기온 (°C)
    private Integer pop;              // 강수확률 (%)
    private String weatherCondition;  // 날씨 상태 (Clear, Rain 등)
    private String iconUrl;           // 아이콘 URL
    private String outfitTip;         // 복장 및 준비물 추천
    private boolean hasCoordinates;  // 좌표 존재 여부 (API 호출 성공 여부)
}