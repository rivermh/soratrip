package com.rivermh.soratrip.domain.schedule.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum ScheduleTag {
    SOLO("혼자여행", "一人旅"),
    COUPLE("커플여행", "カップル旅行"),
    FAMILY_KIDS("아이 동반", "子連れ"),
    FRIENDS("친구와 함께", "友達と一緒"),
    FOOD("맛집 탐방", "グルメ"),
    NATURE("자연/힐링", "自然/癒し"),
    SHOPPING("쇼핑", "ショッピング"),
    BUDGET("가성비 여행", "低予算旅行"),
    LUXURY("럭셔리 여행", "高級旅行"),
    RELAXATION("휴양", "リラックス"),

    // --- [이동 스트레스 제로 / 배리어프리 태그 신규 추가] ---
    ELEVATOR_PRIORITY("🛗 엘리베이터 우선", "エレベーター優先"),
    STAIR_FREE("🧑‍🦽 계단 없음/평지 추천", "階段なし・バリアフリー"),
    HEAVY_LUGGAGE("🧳 대형 캐리어 추천", "大型スーツケース歓迎"),
    STROLLER_FRIENDLY("👶 유모차 이동 수월", "ベビーカーOK"),
    SENIOR_EASY("👵 부모님/시니어 동반 추천", "シニア・ゆったり");

    private final String koName;
    private final String jaName;

    // 현재 설정된 언어에 맞춰 이름 반환
    public String getDisplayName() {
        Locale currentLocale = LocaleContextHolder.getLocale();
        if (Locale.JAPANESE.getLanguage().equals(currentLocale.getLanguage())) {
            return this.jaName;
        }
        return this.koName;
    }
}