package com.saferoute.global.config;

import com.saferoute.global.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** @ConfigurationProperties 바인딩 대상을 한 곳에서 등록한다. */
@Configuration
@EnableConfigurationProperties({
        JwtProperties.class,
        CorsProperties.class
})
public class PropertiesConfig {
}