package com.saferoute.infrastructure.websocket.config;

import com.saferoute.infrastructure.websocket.security.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// 관리자 대시보드로 훈련 현황을 실시간 전달하기 위한 STOMP WebSocket 설정.
// 관리자 웹 -> 서버 명령은 기존 REST API를 그대로 사용하므로 application destination prefix(/app)는
// 현재 실제로 사용하는 @MessageMapping이 없다. 향후 클라이언트->서버 STOMP 메시지가 필요해지면 사용한다.
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 브라우저 STOMP 클라이언트에서 Authorization 헤더는 CONNECT 프레임으로 전달되므로
        // 핸드셰이크 자체는 인증 없이 열어두고(SecurityConfig에서 permitAll), 인증은
        // StompAuthChannelInterceptor에서 CONNECT 프레임 단계에 수행한다.
        //
        // 쿠키/세션 기반 인증을 쓰지 않으므로 allowCredentials(true)는 사용하지 않는다.
        // (allowedOriginPatterns("*")와 allowCredentials(true)를 함께 쓰지 않기 위함)
        // 프론트가 SockJS fallback을 요구하면 이후 .withSockJS()를 추가한다.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}