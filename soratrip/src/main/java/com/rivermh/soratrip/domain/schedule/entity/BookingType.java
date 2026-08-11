package com.rivermh.soratrip.domain.schedule.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

// 예약 정보(TravelBooking)의 교통/숙박 수단 구분
@Getter
@RequiredArgsConstructor
public enum BookingType {
    FLIGHT("항공편", "飛行機", "✈️"),
    TRAIN("기차/신칸센", "電車/新幹線", "🚄"),
    BUS("버스", "バス", "🚌"),
    FERRY("배편", "船", "⛴️"),
    LODGING("숙소 예약", "宿泊予約", "🏨"),
    ETC("기타", "その他", "🎫");

    private final String koName;
    private final String jaName;
    private final String emoji;

    public String getDisplayName() {
        Locale currentLocale = LocaleContextHolder.getLocale();
        if (Locale.JAPANESE.getLanguage().equals(currentLocale.getLanguage())) {
            return this.jaName;
        }
        return this.koName;
    }
}
