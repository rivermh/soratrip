package com.rivermh.soratrip.domain.chat.dto;

import com.rivermh.soratrip.domain.chat.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private Long chatRoomId;
    private Long senderId;
    private String senderName;
    private String content;
    private LocalDateTime sentAt;

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
            message.getId(),
            message.getChatRoom().getId(),
            message.getSender().getId(),
            message.getSender().getNickname(),
            message.getContent(),
            message.getCreatedAt()
        );
    }
}