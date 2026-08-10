package com.rivermh.soratrip.domain.schedule.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WeatherInfoDto {
    private String mainCondition; // 날씨 상태 (Clear, Rain 등)
    private String description;   // 상세 설명
    private String iconUrl;       // 아이콘 이미지 URL
    private Double temp;          // 기온 (°C)
    private Integer pop;          // 강수확률 (%)
    private String outfitTip;     // 복장 및 준비물 팁
}