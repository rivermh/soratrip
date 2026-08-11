package com.rivermh.soratrip.domain.chat.repository;

import com.rivermh.soratrip.domain.chat.entity.ChatMessage;
import com.rivermh.soratrip.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 특정 채팅방의 모든 메시지 조회 (최신순)
    @Query("""
        SELECT m FROM ChatMessage m 
        WHERE m.chatRoom = :chatRoom
        ORDER BY m.createdAt ASC
    """)
    List<ChatMessage> findAllByChatRoom(@Param("chatRoom") ChatRoom chatRoom);

    // 특정 채팅방의 최근 메시지 조회
    @Query(value = """
        SELECT m FROM ChatMessage m 
        WHERE m.chatRoom = :chatRoom
        ORDER BY m.createdAt DESC
        LIMIT :limit
    """)
    List<ChatMessage> findRecentMessagesByChatRoom(@Param("chatRoom") ChatRoom chatRoom, 
                                                    @Param("limit") int limit);

    // 특정 채팅방의 메시지 개수
    Long countByChatRoom(ChatRoom chatRoom);

    // 회원 탈퇴용: 탈퇴 회원이 속한 채팅방들의 메시지 일괄 삭제
    void deleteByChatRoomIn(List<ChatRoom> chatRooms);
}