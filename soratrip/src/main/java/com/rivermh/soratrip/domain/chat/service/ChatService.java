package com.rivermh.soratrip.domain.chat.service;

import com.rivermh.soratrip.domain.chat.dto.ChatMessageResponse;
import com.rivermh.soratrip.domain.chat.dto.ChatRoomResponse;
import com.rivermh.soratrip.domain.chat.entity.ChatMessage;
import com.rivermh.soratrip.domain.chat.entity.ChatRoom;
import com.rivermh.soratrip.domain.chat.repository.ChatMessageRepository;
import com.rivermh.soratrip.domain.chat.repository.ChatRoomRepository;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import com.rivermh.soratrip.domain.notification.entity.NotificationType;
import com.rivermh.soratrip.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemberRepository memberRepository;
    private final NotificationService notificationService;

    // contextTitle 컬럼 길이(VARCHAR 255)를 넘지 않도록 알림 미리보기 텍스트를 잘라내는 최대 길이
    private static final int NOTIFICATION_PREVIEW_MAX_LENGTH = 50;

    /**
     * 두 사용자 간의 채팅방 생성 또는 기존 채팅방 반환
     */
    public ChatRoom getOrCreateChatRoom(Long currentUserId, Long otherUserId) {
        Member currentUser = memberRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        Member otherUser = memberRepository.findById(otherUserId)
                .orElseThrow(() -> new IllegalArgumentException("상대방을 찾을 수 없습니다."));

        // 기존 채팅방 찾기 (종료된 채팅방이면 다시 활성화해서 재사용)
        ChatRoom chatRoom = chatRoomRepository.findChatRoomBetween(currentUser, otherUser)
                .orElseGet(() -> {
                    ChatRoom newRoom = ChatRoom.builder()
                            .initiator(currentUser)
                            .participant(otherUser)
                            .build();
                    return chatRoomRepository.save(newRoom);
                });
        chatRoom.reopen();
        return chatRoom;
    }

    /**
     * 메시지 저장
     */
    public ChatMessageResponse saveMessage(Long chatRoomId, Long senderId, String content) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        Member sender = memberRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!chatRoom.contains(sender)) {
            throw new IllegalArgumentException("이 채팅방에 접근할 권한이 없습니다.");
        }
        if (!chatRoom.isActive()) {
            throw new IllegalStateException("종료되었거나 차단된 채팅방에는 메시지를 보낼 수 없습니다.");
        }

        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(content)
                .build();

        ChatMessage saved = chatMessageRepository.save(message);
        log.info("메시지 저장됨 - ChatRoom: {}, Sender: {}", chatRoomId, senderId);

        Member recipient = chatRoom.getOtherMember(sender);
        notificationService.notify(recipient, sender, NotificationType.CHAT_MESSAGE, chatRoom.getId(), truncateForPreview(content));

        return ChatMessageResponse.from(saved);
    }

    private String truncateForPreview(String content) {
        if (content == null || content.length() <= NOTIFICATION_PREVIEW_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, NOTIFICATION_PREVIEW_MAX_LENGTH) + "...";
    }

    /**
     * 특정 채팅방의 모든 메시지 조회
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        Member user = memberRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!chatRoom.contains(user)) {
            throw new IllegalArgumentException("이 채팅방에 접근할 권한이 없습니다.");
        }

        List<ChatMessage> messages = chatMessageRepository.findAllByChatRoom(chatRoom);
        return messages.stream()
                .map(ChatMessageResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 사용자의 모든 채팅방 조회
     */
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getMyChatRooms(Long userId) {
        Member user = memberRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        List<ChatRoom> chatRooms = chatRoomRepository.findAllByMember(user);
        return chatRooms.stream()
                .map(room -> ChatRoomResponse.from(room, userId))
                .collect(Collectors.toList());
    }

    /**
     * 채팅방 종료
     */
    public void closeChatRoom(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        Member user = memberRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!chatRoom.contains(user)) {
            throw new IllegalArgumentException("이 채팅방에 접근할 권한이 없습니다.");
        }

        chatRoom.close();
        chatRoomRepository.save(chatRoom);
        log.info("채팅방 종료 - ChatRoom: {}", chatRoomId);
    }

    /**
     * 채팅방 접근 권한 확인
     */
    @Transactional(readOnly = true)
    public boolean hasAccessToChatRoom(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElse(null);
        if (chatRoom == null) return false;
        
        Member user = memberRepository.findById(userId).orElse(null);
        return user != null && chatRoom.contains(user);
    }
}