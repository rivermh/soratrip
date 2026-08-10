package com.rivermh.soratrip.domain.schedule.dto;

import lombok.Getter;
import lombok.Setter;

// 하루 단위 AI 재생성 요청 (해당 날짜에 대한 추가 요청사항)
@Getter
@Setter
public class DayRegenerateRequest {
    private String extraPrompt;
}
