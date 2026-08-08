package com.rivermh.soratrip.domain.chat.entity;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Builder
    public ChatMessage(ChatRoom chatRoom, Member sender, String content) {
        this.chatRoom = chatRoom;
        this.sender = sender;
        this.content = content;
    }
}