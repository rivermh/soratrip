package com.rivermh.soratrip.domain.post.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum Region {
    TOKYO("도쿄", "東京"),
    OSAKA("오사카", "大阪"),
    FUKUOKA("후쿠오카", "福岡"),
    HOKKAIDO("홋카이도", "北海道"),
    SEOUL("서울", "ソウル"),
    BUSAN("부산", "釜山"),
    JEJU("제주", "済州");

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