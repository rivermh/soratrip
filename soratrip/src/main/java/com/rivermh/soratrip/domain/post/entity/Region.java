package com.rivermh.soratrip.domain.post.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum Region {
    // 파라미터: 한글명, 일본어명, IANA 시간대(iCal 내보내기용), 대략적인 위도/경도 범위(AI 좌표 검증용).
    // 좌표 범위는 인접 당일치기 명소(가마쿠라/요코하마/교토/나라 등)까지 포용하도록 넉넉하게 잡는다.
    TOKYO("도쿄", "東京", "Asia/Tokyo", 34.9, 36.8, 138.5, 140.6),
    OSAKA("오사카", "大阪", "Asia/Tokyo", 34.0, 35.3, 134.8, 136.2),
    FUKUOKA("후쿠오카", "福岡", "Asia/Tokyo", 32.5, 34.0, 129.5, 131.5),
    HOKKAIDO("홋카이도", "北海道", "Asia/Tokyo", 41.3, 45.6, 139.3, 145.9),
    NAGOYA("나고야", "名古屋", "Asia/Tokyo", 34.5, 35.5, 136.3, 137.5),
    OKINAWA("오키나와", "沖縄", "Asia/Tokyo", 24.0, 27.9, 122.9, 131.3),
    SEOUL("서울", "ソウル", "Asia/Seoul", 37.0, 38.0, 126.4, 127.5),
    BUSAN("부산", "釜山", "Asia/Seoul", 34.8, 35.5, 128.7, 129.3),
    JEJU("제주", "済州", "Asia/Seoul", 33.1, 33.6, 126.1, 126.9),
    INCHEON("인천", "仁川", "Asia/Seoul", 37.2, 37.8, 126.1, 126.8);

    private final String koName;
    private final String jaName;
    private final String timezoneId;
    private final double latMin;
    private final double latMax;
    private final double lonMin;
    private final double lonMax;

    // 현재 설정된 언어에 맞춰 이름 반환
    public String getDisplayName() {
        Locale currentLocale = LocaleContextHolder.getLocale();
        if (Locale.JAPANESE.getLanguage().equals(currentLocale.getLanguage())) {
            return this.jaName;
        }
        return this.koName;
    }
}