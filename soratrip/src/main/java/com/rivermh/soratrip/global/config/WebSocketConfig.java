package com.rivermh.soratrip.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 메시지 브로커 설정
     * - /topic: 1:N (모든 구독자에게 브로드캐스트)
     * - /queue: 1:1 (특정 사용자에게만)
     * - /app: 클라이언트에서 서버로 보낼 때의 prefix
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 메시지 브로커 활성화
        config.enableSimpleBroker("/topic", "/queue", "/user");
        
        // 클라이언트의 요청을 받을 prefix
        config.setApplicationDestinationPrefixes("/app");
        
        // 1:1 메시지용 prefix (예: /user/{username}/queue/messages)
        config.setUserDestinationPrefix("/user");
    }

    /**
     * WebSocket 엔드포인트 등록
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*")  // CORS 자격 증명 충돌 방지를 위해 allowedOriginPatterns 사용
                .withSockJS();  // SockJS 폴백 활성화 (WebSocket 미지원 환경에서도 동작)
    }
}