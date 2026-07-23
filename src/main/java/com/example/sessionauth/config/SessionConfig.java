package com.example.sessionauth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

// Spring Boot 4에서 자동설정이 안 걸리는 경우, HttpSession을 Redis에 저장하도록 명시적으로 활성화.
@Configuration
@EnableRedisHttpSession
public class SessionConfig {
}
