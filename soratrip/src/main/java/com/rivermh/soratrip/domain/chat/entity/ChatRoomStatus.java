package com.rivermh.soratrip.domain.chat.entity;

public enum ChatRoomStatus {
    ACTIVE("활성"),
    CLOSED("종료"),
    BLOCKED("차단");

    private final String displayName;

    ChatRoomStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}