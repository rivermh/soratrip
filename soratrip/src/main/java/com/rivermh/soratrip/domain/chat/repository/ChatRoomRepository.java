package com.rivermh.soratrip.domain.chat.repository;

import com.rivermh.soratrip.domain.chat.entity.ChatRoom;
import com.rivermh.soratrip.domain.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // 두 사용자 간의 기존 채팅방 찾기 (괄호 위치 수정)
    @Query("""
        SELECT c FROM ChatRoom c 
        WHERE ((c.initiator = :member1 AND c.participant = :member2) 
           OR (c.initiator = :member2 AND c.participant = :member1))
        AND c.status = 'ACTIVE'
    """)
    Optional<ChatRoom> findActiveChatRoom(@Param("member1") Member member1, 
                                          @Param("member2") Member member2);

    // 특정 사용자의 모든 활성 채팅방
    @Query("""
        SELECT c FROM ChatRoom c 
        WHERE (c.initiator = :member OR c.participant = :member)
        AND c.status = 'ACTIVE'
        ORDER BY c.createdAt DESC
    """)
    List<ChatRoom> findAllByMember(@Param("member") Member member);

    // 특정 사용자와의 활성 채팅방 조회 (괄호 위치 수정)
    @Query("""
        SELECT c FROM ChatRoom c 
        WHERE ((c.initiator = :currentUser AND c.participant = :otherUser) 
           OR (c.initiator = :otherUser AND c.participant = :currentUser))
        AND c.status = 'ACTIVE'
    """)
    Optional<ChatRoom> findChatRoomBetween(@Param("currentUser") Member currentUser,
                                           @Param("otherUser") Member otherUser);

    // 회원 탈퇴용: 상태(활성/종료/차단) 상관없이 탈퇴 회원이 속한 모든 채팅방 조회
    List<ChatRoom> findAllByInitiatorOrParticipant(Member initiator, Member participant);
}