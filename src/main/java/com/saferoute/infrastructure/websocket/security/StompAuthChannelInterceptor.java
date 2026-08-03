package com.saferoute.infrastructure.websocket.security;

import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.global.security.CustomUserDetailsService;
import com.saferoute.global.security.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

// STOMP CONNECT 프레임의 Authorization 헤더로 기존 JWT 컴포넌트를 재사용해 인증한다.
// 관리자 대시보드 WebSocket 연결/구독은 MANAGER 권한만 허용한다(=이번 작업에서의 "ADMIN").
//
// 인증에 성공하면 accessor.setUser(...)로 Principal을 STOMP 세션에 저장한다.
// 이후 프레임(SUBSCRIBE 등)에서는 Spring이 세션에 저장된 Principal을 accessor.getUser()로 복원해준다.
//
// 토큰 원문/이메일 등 개인정보는 로그에 남기지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MANAGER_AUTHORITY = "ROLE_MANAGER";
    private static final String USER_ERROR_DESTINATION = "/user/queue/errors";
    private static final Pattern SESSION_TOPIC_PATTERN =
            Pattern.compile("^/topic/training-sessions/([0-9a-fA-F-]{36})$");

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TrainingSessionRepository trainingSessionRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");

        String token = resolveToken(authorization);

        if (token == null) {
            log.warn("WebSocket CONNECT 거부: Authorization 헤더가 없거나 형식이 올바르지 않습니다.");
            throw new AuthenticationCredentialsNotFoundException("Authorization 헤더가 필요합니다.");
        }

        UserDetails userDetails;

        try {
            String email = jwtTokenProvider.getEmail(token);
            userDetails = userDetailsService.loadUserByUsername(email);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException exception) {
            log.warn("WebSocket CONNECT 거부: 유효하지 않은 JWT입니다.");
            throw new BadCredentialsException("유효하지 않은 JWT입니다.", exception);
        }

        if (!hasManagerAuthority(userDetails)) {
            log.warn("WebSocket CONNECT 거부: MANAGER 권한이 아닙니다.");
            throw new AccessDeniedException("WebSocket 연결은 MANAGER 권한만 가능합니다.");
        }

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        accessor.setUser(authentication);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        Authentication authentication = resolveAuthentication(accessor);

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("WebSocket SUBSCRIBE 거부: 인증되지 않은 세션입니다.");
            throw new AuthenticationCredentialsNotFoundException("인증이 필요합니다.");
        }

        boolean isManager = authentication.getAuthorities().stream()
                .anyMatch(authority -> MANAGER_AUTHORITY.equals(authority.getAuthority()));

        if (!isManager) {
            log.warn("WebSocket SUBSCRIBE 거부: MANAGER 권한이 아닙니다.");
            throw new AccessDeniedException("구독 권한이 없습니다.");
        }

        String destination = accessor.getDestination();

        if (destination == null) {
            log.warn("WebSocket SUBSCRIBE 거부: destination이 없습니다.");
            throw new IllegalArgumentException("destination이 필요합니다.");
        }

        if (destination.startsWith(USER_ERROR_DESTINATION)) {
            return;
        }

        Matcher matcher = SESSION_TOPIC_PATTERN.matcher(destination);

        if (!matcher.matches()) {
            log.warn("WebSocket SUBSCRIBE 거부: 지원하지 않는 destination입니다.");
            throw new IllegalArgumentException("지원하지 않는 구독 경로입니다.");
        }

        // 세션 존재 여부만 검증한다.
        // 관리자별로 "본인이 담당하는 세션만 구독 가능"하도록 제한하려면 User-TrainingSession
        // 소유 관계가 필요한데, 현재 도메인 모델에는 그런 관계가 없어(모든 MANAGER가 모든
        // TrainingSession에 접근 가능한 구조) 역할 기반 제한 이상은 적용하지 않았다.
        UUID sessionId = UUID.fromString(matcher.group(1));

        if (!trainingSessionRepository.existsById(sessionId)) {
            log.warn("WebSocket SUBSCRIBE 거부: 존재하지 않는 훈련 세션입니다.");
            throw new IllegalArgumentException("존재하지 않는 훈련 세션입니다.");
        }
    }

    private Authentication resolveAuthentication(StompHeaderAccessor accessor) {
        Object user = accessor.getUser();
        return user instanceof Authentication ? (Authentication) user : null;
    }

    private boolean hasManagerAuthority(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .anyMatch(authority -> MANAGER_AUTHORITY.equals(authority.getAuthority()));
    }

    private String resolveToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();

        return token.isEmpty() ? null : token;
    }
}