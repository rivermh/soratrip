package com.rivermh.soratrip.domain.chat.dto;

import com.rivermh.soratrip.domain.chat.entity.ChatRoom;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomResponse {
    private Long id;
    private Long otherUserId;
    private String otherUserName;
    private String otherUserNickname;
    private String status;
    private LocalDateTime createdAt;

    public static ChatRoomResponse from(ChatRoom chatRoom, Long currentUserId) {
        var otherUser = chatRoom.getInitiator().getId().equals(currentUserId) 
            ? chatRoom.getParticipant() 
            : chatRoom.getInitiator();
            
        return new ChatRoomResponse(
            chatRoom.getId(),
            otherUser.getId(),
            otherUser.getEmail(),
            otherUser.getNickname(),
            chatRoom.getStatus().name(),
            chatRoom.getCreatedAt()
        );
    }
}