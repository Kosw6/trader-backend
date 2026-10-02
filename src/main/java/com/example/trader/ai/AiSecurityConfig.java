package com.example.trader.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import java.time.Clock;

@Configuration
public class AiSecurityConfig {
    @Bean @Order(1)
    SecurityFilterChain aiInternalChain(HttpSecurity http,
            @Value("${ai.internal.enabled:false}") boolean enabled,
            @Value("${ai.internal.retrieval-key:}") String retrievalKey,
            @Value("${ai.internal.indexing-key:}") String indexingKey,
            @Value("${ai.internal.execution-seconds:120}") int executionSeconds,
            @Value("${ai.internal.clock-skew-seconds:5}") int skewSeconds, AiCursorCodec cursor) throws Exception {
        if(enabled && !cursor.configured()) throw new IllegalArgumentException("AI 내부 조회에는 커서 서명 키가 필요합니다.");
        // 필터를 서블릿 전역 Bean으로 등록하지 않는다. 이 체인 밖 사용자 인증에 영향을 주지 않음.
        return http.securityMatcher("/internal/ai", "/internal/ai/**")
                .csrf(c -> c.disable()).requestCache(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(new AiServiceAuthFilter(enabled, retrievalKey, indexingKey,
                        executionSeconds, skewSeconds, Clock.systemUTC()), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
