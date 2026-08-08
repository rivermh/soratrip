package com.rivermh.soratrip.domain.chat.controller;

import com.rivermh.soratrip.domain.chat.dto.ChatMessageRequest;
import com.rivermh.soratrip.domain.chat.dto.ChatMessageResponse;
import com.rivermh.soratrip.domain.chat.dto.ChatRoomResponse;
import com.rivermh.soratrip.domain.chat.service.ChatService;
import com.rivermh.soratrip.domain.chat.service.MatchingService;
import com.rivermh.soratrip.domain.member.entity.Member;
import com.rivermh.soratrip.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final MatchingService matchingService;
    private final MemberRepository memberRepository; // 이메일로 회원 ID를 찾기 위해 추가
    private final SimpMessagingTemplate simpMessagingTemplate;

    /**
     * ==================== WebSocket + STOMP (실시간 채팅) ====================
     */

    /**
     * 클라이언트가 /app/chat/{chatRoomId} 로 메시지 보내면
     * 해당 채팅방을 구독하는 모든 사람에게 브로드캐스트 (/topic/chat/{chatRoomId})
     */
    
    /**
     * 현재 로그인한 사용자의 ID 조회
     * GET /api/chat/my-id
     */
    @GetMapping("/my-id")
    public ResponseEntity<Long> getMyId(Principal principal) {
        Long userId = extractUserId(principal);
        return ResponseEntity.ok(userId);
    }
    
    @MessageMapping("/chat/{chatRoomId}")
    @SendTo("/topic/chat/{chatRoomId}")
    public ChatMessageResponse sendMessage(
            @DestinationVariable Long chatRoomId,
            ChatMessageRequest request,
            Principal principal) {
        
        // 1. 로그인 유저 확인 및 DB ID 추출
        Long userId = extractUserId(principal);
        log.info("메시지 수신 - ChatRoomId: {}, SenderId: {}", chatRoomId, userId);
        
        // 2. 요청 객체에 방 번호가 비어있다면 세팅
        if (request.getChatRoomId() == null) {
            request.setChatRoomId(chatRoomId);
        }
        
        // 3. 메시지 DB 저장 및 응답 DTO 생성
        ChatMessageResponse response = chatService.saveMessage(chatRoomId, userId, request.getContent());
        
        log.info("메시지 브로드캐스트 완료 - 내용: {}", request.getContent());
        
        // 4. 반환된 응답 객체는 @SendTo에 의해 구독자들에게 자동 전송됨
        return response;
    }

    /**
     * ==================== REST API (채팅방 관리) ====================
     */

    @GetMapping("/rooms/{userId}")
    public ResponseEntity<Long> getOrCreateChatRoom(
            @PathVariable("userId") Long userId, 
            Principal principal) {
        
        Long currentUserId = extractUserId(principal);
        Long chatRoomId = chatService.getOrCreateChatRoom(currentUserId, userId).getId();
        
        return ResponseEntity.ok(chatRoomId);
    }

    @GetMapping("/my-rooms")
    public ResponseEntity<List<ChatRoomResponse>> getMyChatRooms(Principal principal) {
        Long userId = extractUserId(principal);
        List<ChatRoomResponse> rooms = chatService.getMyChatRooms(userId);
        
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/rooms/{chatRoomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable Long chatRoomId,
            Principal principal) {
        
        Long userId = extractUserId(principal);
        
        if (!chatService.hasAccessToChatRoom(chatRoomId, userId)) {
            return ResponseEntity.status(403).build();
        }
        
        List<ChatMessageResponse> messages = chatService.getMessages(chatRoomId, userId);
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/rooms/{chatRoomId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessageRest(
            @PathVariable Long chatRoomId,
            @RequestBody ChatMessageRequest request,
            Principal principal) {
        
        Long userId = extractUserId(principal);
        
        if (!chatService.hasAccessToChatRoom(chatRoomId, userId)) {
            return ResponseEntity.status(403).build();
        }
        
        ChatMessageResponse response = chatService.saveMessage(chatRoomId, userId, request.getContent());
        
        // REST로 요청이 들어온 경우에도 STOMP 구독자들에게 실시간 브로드캐스트
        simpMessagingTemplate.convertAndSend(
            "/topic/chat/" + chatRoomId,
            response
        );
        
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/rooms/{chatRoomId}")
    public ResponseEntity<Void> closeChatRoom(
            @PathVariable Long chatRoomId,
            Principal principal) {
        
        Long userId = extractUserId(principal);
        
        if (!chatService.hasAccessToChatRoom(chatRoomId, userId)) {
            return ResponseEntity.status(403).build();
        }
        
        chatService.closeChatRoom(chatRoomId, userId);
        return ResponseEntity.ok().build();
    }

    /**
     * ==================== 매칭 API ====================
     */

    @GetMapping("/matching/similar-users")
    public ResponseEntity<List<MatchingService.MemberMatchInfo>> findSimilarUsers(
            Principal principal) {
        
        Long userId = extractUserId(principal);
        List<MatchingService.MemberMatchInfo> users = matchingService.findSimilarUsers(userId);
        
        return ResponseEntity.ok(users);
    }

    @GetMapping("/matching/by-region/{region}")
    public ResponseEntity<List<MatchingService.MemberMatchInfo>> findUsersByRegion(
            @PathVariable String region,
            Principal principal) {
        
        Long userId = extractUserId(principal);
        List<MatchingService.MemberMatchInfo> users = matchingService.findUsersByRegion(userId, region);
        
        return ResponseEntity.ok(users);
    }

    /**
     * ==================== 유틸리티 ====================
     */

    /**
     * Principal(이메일)을 기반으로 Member 엔티티를 조회하여 실제 PK(userId)를 반환
     */
    private Long extractUserId(Principal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("인증된 사용자가 없습니다.");
        }
        
        String email = principal.getName();
        
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. 이메일: " + email));
                
        return member.getId();
    }
}