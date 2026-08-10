package com.rivermh.soratrip.domain.schedule.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class OpenWeatherResponseDto {

    private List<ForecastItem> list;

    @Getter
    @NoArgsConstructor
    public static class ForecastItem {
        private Long dt; // Unix timestamp
        @JsonProperty("dt_txt")
        private String dtTxt; // "2026-08-10 12:00:00"

        private Main main;
        private List<Weather> weather;
        private Double pop; // 강수확률 (0.0 ~ 1.0)

        @Getter
        @NoArgsConstructor
        public static class Main {
            private Double temp;
            @JsonProperty("temp_min")
            private Double tempMin;
            @JsonProperty("temp_max")
            private Double tempMax;
            private Integer humidity;
        }

        @Getter
        @NoArgsConstructor
        public static class Weather {
            private String main; // Clear, Rain, Clouds 등
            private String description; // 상세 설명 (맑음, 얇은 구름 등)
            private String icon; // 아이콘 코드 (예: 10d)
        }
    }
}