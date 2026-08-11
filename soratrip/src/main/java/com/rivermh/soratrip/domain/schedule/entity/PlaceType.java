package com.rivermh.soratrip.domain.schedule.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

// 일정에 추가한 개별 장소(ScheduleItem)의 유형 (맛집/카페/명소 등 구분 표시용)
@Getter
@RequiredArgsConstructor
public enum PlaceType {
    ATTRACTION("명소", "観光地", "🏛️"),
    RESTAURANT("맛집", "グルメ", "🍜"),
    CAFE("카페", "カフェ", "☕"),
    LODGING("숙소", "宿泊", "🏨"),
    TRANSPORT("이동/교통", "交通", "🚗"),
    SHOPPING("쇼핑", "ショッピング", "🛍️"),
    ETC("기타", "その他", "📍");

    private final String koName;
    private final String jaName;
    private final String emoji;

    // 현재 설정된 언어에 맞춰 이름 반환
    public String getDisplayName() {
        Locale currentLocale = LocaleContextHolder.getLocale();
        if (Locale.JAPANESE.getLanguage().equals(currentLocale.getLanguage())) {
            return this.jaName;
        }
        return this.koName;
    }
}
