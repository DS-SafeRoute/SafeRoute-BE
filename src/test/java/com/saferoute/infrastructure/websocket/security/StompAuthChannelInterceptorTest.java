package com.saferoute.infrastructure.websocket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.saferoute.domain.training.repository.TrainingSessionRepository;
import com.saferoute.global.security.CustomUserDetailsService;
import com.saferoute.global.security.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private TrainingSessionRepository trainingSessionRepository;

    @Mock
    private MessageChannel channel;

    private StompAuthChannelInterceptor interceptor;

    private static final String VALID_TOKEN = "valid-token";
    private static final String MANAGER_EMAIL = "manager@saferoute.com";
    private static final String NORMAL_EMAIL = "normal@saferoute.com";

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(
                jwtTokenProvider,
                userDetailsService,
                trainingSessionRepository
        );
    }

    @Test
    @DisplayName("유효한 MANAGER JWT로 CONNECT하면 인증된 Principal이 세션에 저장된다")
    void connectSucceedsWithValidManagerToken() {
        given(jwtTokenProvider.getEmail(VALID_TOKEN)).willReturn(MANAGER_EMAIL);
        given(userDetailsService.loadUserByUsername(MANAGER_EMAIL))
                .willReturn(managerUserDetails());

        StompHeaderAccessor accessor = connectAccessor("Bearer " + VALID_TOKEN);
        Message<?> message = build(accessor);

        interceptor.preSend(message, channel);

        assertThat(accessor.getUser()).isNotNull();

        Authentication authentication = (Authentication) accessor.getUser();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .contains("ROLE_MANAGER");
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 CONNECT를 거부한다")
    void connectRejectsWithoutAuthorizationHeader() {
        StompHeaderAccessor accessor = connectAccessor(null);
        Message<?> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("Bearer 형식이 아니면 CONNECT를 거부한다")
    void connectRejectsMalformedBearerHeader() {
        StompHeaderAccessor accessor = connectAccessor(VALID_TOKEN);
        Message<?> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("유효하지 않은 JWT면 CONNECT를 거부한다")
    void connectRejectsInvalidJwt() {
        given(jwtTokenProvider.getEmail(VALID_TOKEN))
                .willThrow(new JwtException("서명이 유효하지 않습니다."));

        StompHeaderAccessor accessor = connectAccessor("Bearer " + VALID_TOKEN);
        Message<?> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("만료된 JWT면 CONNECT를 거부한다")
    void connectRejectsExpiredJwt() {
        given(jwtTokenProvider.getEmail(VALID_TOKEN))
                .willThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "만료된 토큰입니다."));

        StompHeaderAccessor accessor = connectAccessor("Bearer " + VALID_TOKEN);
        Message<?> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 CONNECT를 거부한다")
    void connectRejectsUnknownUser() {
        given(jwtTokenProvider.getEmail(VALID_TOKEN)).willReturn(MANAGER_EMAIL);
        given(userDetailsService.loadUserByUsername(MANAGER_EMAIL))
                .willThrow(new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        StompHeaderAccessor accessor = connectAccessor("Bearer " + VALID_TOKEN);
        Message<?> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("NORMAL 권한이면 CONNECT를 거부한다")
    void connectRejectsNormalRole() {
        given(jwtTokenProvider.getEmail(VALID_TOKEN)).willReturn(NORMAL_EMAIL);
        given(userDetailsService.loadUserByUsername(NORMAL_EMAIL))
                .willReturn(normalUserDetails());

        StompHeaderAccessor accessor = connectAccessor("Bearer " + VALID_TOKEN);
        Message<?> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("인증된 MANAGER는 존재하는 세션 topic을 구독할 수 있다")
    void subscribeSucceedsForAuthenticatedManagerWithExistingSession() {
        UUID sessionId = UUID.randomUUID();
        given(trainingSessionRepository.existsById(sessionId)).willReturn(true);

        StompHeaderAccessor accessor =
                subscribeAccessor("/topic/training-sessions/" + sessionId, managerAuthentication());
        Message<?> message = build(accessor);

        org.assertj.core.api.Assertions.assertThatCode(() -> interceptor.preSend(message, channel))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("인증되지 않은 세션은 SUBSCRIBE를 거부한다")
    void subscribeRejectsWithoutAuthentication() {
        StompHeaderAccessor accessor =
                subscribeAccessor("/topic/training-sessions/" + UUID.randomUUID(), null);
        Message<?> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    @DisplayName("NORMAL 권한으로는 SUBSCRIBE를 거부한다")
    void subscribeRejectsNormalRole() {
        StompHeaderAccessor accessor = subscribeAccessor(
                "/topic/training-sessions/" + UUID.randomUUID(),
                normalAuthentication()
        );
        Message<?> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 세션 topic 구독은 거부한다")
    void subscribeRejectsUnknownSession() {
        UUID sessionId = UUID.randomUUID();
        given(trainingSessionRepository.existsById(sessionId)).willReturn(false);

        StompHeaderAccessor accessor =
                subscribeAccessor("/topic/training-sessions/" + sessionId, managerAuthentication());
        Message<?> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("지원하지 않는 destination 형식은 거부한다")
    void subscribeRejectsInvalidDestination() {
        StompHeaderAccessor accessor =
                subscribeAccessor("/topic/something-else", managerAuthentication());
        Message<?> message = build(accessor);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("사용자별 오류 큐 구독은 세션 존재 검증 없이 허용한다")
    void subscribeAllowsUserErrorQueueWithoutSessionCheck() {
        StompHeaderAccessor accessor =
                subscribeAccessor("/user/queue/errors", managerAuthentication());
        Message<?> message = build(accessor);

        interceptor.preSend(message, channel);
        // trainingSessionRepository.existsById가 호출되지 않아도 예외 없이 통과해야 한다.
    }

    private StompHeaderAccessor connectAccessor(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        accessor.setSessionId(UUID.randomUUID().toString());
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private StompHeaderAccessor subscribeAccessor(String destination, Authentication authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionId(UUID.randomUUID().toString());
        accessor.setLeaveMutable(true);
        if (authentication != null) {
            accessor.setUser(authentication);
        }
        return accessor;
    }

    private Message<?> build(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private UserDetails managerUserDetails() {
        return new org.springframework.security.core.userdetails.User(
                MANAGER_EMAIL,
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_MANAGER"))
        );
    }

    private UserDetails normalUserDetails() {
        return new org.springframework.security.core.userdetails.User(
                NORMAL_EMAIL,
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_NORMAL"))
        );
    }

    private Authentication managerAuthentication() {
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(managerUserDetails(), null, managerUserDetails().getAuthorities());
    }

    private Authentication normalAuthentication() {
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(normalUserDetails(), null, normalUserDetails().getAuthorities());
    }
}