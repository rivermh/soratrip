package com.rivermh.soratrip.domain.post.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum Category {
    COMPANION("동행 구하기", "同行募集"),
    INFO("여행 정보", "旅行情報"),
    FREE("자유게시판", "自由掲示板");

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