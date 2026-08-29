package com.saferoute.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.domain.device.repository.CctvJpaRepository;
import com.saferoute.global.security.CustomUserDetailsService;
import com.saferoute.global.security.DeviceAuthenticationFilter;
import com.saferoute.global.security.DeviceTokenService;
import com.saferoute.global.security.JwtAccessDeniedHandler;
import com.saferoute.global.security.JwtAuthenticationEntryPoint;
import com.saferoute.global.security.JwtAuthenticationFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authenticationProvider,
            CorsConfigurationSource corsConfigurationSource,
            DeviceTokenService deviceTokenService,
            CctvJpaRepository cctvJpaRepository,
            ObjectMapper objectMapper
    ) throws Exception {

        DeviceAuthenticationFilter deviceAuthenticationFilter =
                new DeviceAuthenticationFilter(
                        deviceTokenService,
                        cctvJpaRepository,
                        objectMapper
                );

        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                jwtAuthenticationEntryPoint
                        )
                        .accessDeniedHandler(
                                jwtAccessDeniedHandler
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        // 브라우저 preflight만 공개한다.
                        // 실제 API 요청의 권한 규칙은 아래에서 유지한다.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/ws/**"
                        ).permitAll()

                        // /actuator/**를 명시적으로 분리한다.
                        // health만 공개하고, 그 외(env, beans, metrics 등)는 인증 필요.
                        // 실제로 어떤 actuator endpoint가 웹에 노출되는지는
                        // application.yml의 management 설정으로 별도 통제한다.
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").authenticated()

                        .requestMatchers("/api/v1/device/**").hasRole("DEVICE")

                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/v1/users/me"
                        ).hasAnyRole("MANAGER", "NORMAL")

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/logout"
                        ).hasAnyRole("MANAGER", "NORMAL")

                        // 도면 GridCell 원본 데이터는 CCTV 감시 영역을 설정하는 관리자 기능에서만 사용한다.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/floors/*/grid/cells"
                        ).hasRole("MANAGER")

                        // 재탐색 승인 대기 목록/상세는 관리자 화면 전용이라 일반 GET 허용 규칙보다 먼저 좁혀둔다.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/route-recalculations",
                                "/api/v1/route-recalculations/*"
                        ).hasRole("MANAGER")

                        // 혼잡 이미지 조회 URL도 관리자 화면 전용이다.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/congestion-events/*/image-url",
                                "/api/v1/congestion-observations/*/image-url"
                        ).hasRole("MANAGER")

                        // 훈련 모니터링 카메라와 캡처 이미지는 관리자 화면에서만 조회한다.
                        // 카메라 카드 목록(/cameras), 프레임 목록(/cameras/{cctvId}/frames), 이벤트
                        // 타임라인(/events)을 모두 포함해야 한다.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/sessions/*/monitoring/cameras",
                                "/api/v1/sessions/*/monitoring/cameras/**",
                                "/api/v1/sessions/*/monitoring/events"
                        ).hasRole("MANAGER")

                        // 세션 목록은 모니터링 화면 진입점으로 쓰이므로 카메라 조회와 동일하게 관리자 전용이다.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/sessions"
                        ).hasRole("MANAGER")

                        // 경로 이탈률은 훈련 결과 분석용 관리자 화면 전용이다.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/lights/*/deviation"
                        ).hasRole("MANAGER")

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/**"
                        ).hasAnyRole("MANAGER", "NORMAL")

                        .requestMatchers("/api/**")
                        .hasRole("MANAGER")

                        .anyRequest()
                        .authenticated()
                )

                .authenticationProvider(authenticationProvider)

                .addFilterBefore(
                        deviceAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            CorsProperties corsProperties
    ) {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                corsProperties.allowedOrigins()
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of(
                        "Authorization",
                        "Content-Type"
                )
        );

        // WebSocket handshake에서 쿠키 기반 인증 정보를 전달하므로 유지한다.
        // allowedOrigins는 "*"가 아닌 명시적인 Origin 목록이어야 한다.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);

        provider.setPasswordEncoder(passwordEncoder);

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
