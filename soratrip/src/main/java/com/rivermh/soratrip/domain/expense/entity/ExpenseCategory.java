package com.rivermh.soratrip.domain.expense.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum ExpenseCategory {
    FOOD("식비", "食費"),
    TRANSPORT("교통", "交通費"),
    LODGING("숙박", "宿泊費"),
    ACTIVITY("액티비티", "アクティビティ"),
    SHOPPING("쇼핑", "ショッピング"),
    ETC("기타", "その他");

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
