package com.saferoute.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// @EnableJpaAuditing을 SafeRouteApplication에서 분리한 이유:
// 메인 클래스에 직접 붙어있으면 @WebMvcTest 슬라이스에서도 이 어노테이션이 그대로 로드되면서
// JPA 레이어가 없어 jpaMappingContext 빈을 못 찾는 BeanCreationException이 발생함.
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
