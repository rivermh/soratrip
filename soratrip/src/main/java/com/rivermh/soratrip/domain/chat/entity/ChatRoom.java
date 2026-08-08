package com.rivermh.soratrip.domain.chat.entity;

import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_id", nullable = false)
    private Member initiator;  // 채팅 시작한 사람

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Member participant;  // 채팅 상대방

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRoomStatus status = ChatRoomStatus.ACTIVE;

    @Builder
    public ChatRoom(Member initiator, Member participant) {
        this.initiator = initiator;
        this.participant = participant;
        this.status = ChatRoomStatus.ACTIVE;
    }

    // 주어진 member가 이 채팅방에 속하는지 확인
    public boolean contains(Member member) {
        return initiator.getId().equals(member.getId()) || 
               participant.getId().equals(member.getId());
    }

    // 상대방 조회
    public Member getOtherMember(Member member) {
        if (initiator.getId().equals(member.getId())) {
            return participant;
        } else if (participant.getId().equals(member.getId())) {
            return initiator;
        }
        throw new IllegalArgumentException("이 채팅방에 속하지 않는 사용자입니다.");
    }

    // 채팅방 닫기
    public void close() {
        this.status = ChatRoomStatus.CLOSED;
    }
}