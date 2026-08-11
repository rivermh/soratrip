package com.rivermh.soratrip.domain.schedule.dto;

import com.rivermh.soratrip.domain.schedule.entity.ChecklistItem;

import lombok.Getter;

@Getter
public class ChecklistItemResponseDto {

    private final Long id;
    private final String content;
    private final boolean checked;

    public ChecklistItemResponseDto(ChecklistItem item) {
        this.id = item.getId();
        this.content = item.getContent();
        this.checked = item.isChecked();
    }
}
