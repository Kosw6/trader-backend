package com.example.trader.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Configuration
@EnableCaching
public class RedisConfig {

    // 공통 JSON 직렬화기 (JavaTimeModule 등록, ISO-8601로 날짜 기록)
    private GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new GenericJackson2JsonRedisSerializer(om);
    }

    // 공통 직렬화 Pair: 키=문자열, 값=커스텀 JSON
    private RedisSerializationContext.SerializationPair<Object> json() {
        return RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer());
    }

    /** Redis 캐시매니저 (기본 TTL + 캐시별 TTL 설정) */
    @Bean
    public org.springframework.data.redis.cache.RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(json())
                .prefixCacheNameWith("trader::");

        // 캐시 이름별 TTL 커스터마이즈 (예: 1분/1시간)
        Map<String, RedisCacheConfiguration> perCache = Map.of(
                "fastCache", defaultConfig.entryTtl(Duration.ofMinutes(1)),
                "slowCache", defaultConfig.entryTtl(Duration.ofHours(1))
        );

        return org.springframework.data.redis.cache.RedisCacheManager.builder(cf)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    /** 문자열 전용 템플릿 (간단 카운터/락 등에 편함) */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        var tpl = new StringRedisTemplate(cf);
        tpl.setEnableDefaultSerializer(false);
        return tpl;
    }

    /** 객체용 RedisTemplate (값 JSON 직렬화) */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory cf) {
        var tpl = new RedisTemplate<String, Object>();
        tpl.setConnectionFactory(cf);
        tpl.setKeySerializer(new StringRedisSerializer());
        tpl.setValueSerializer(jsonSerializer());
        tpl.setHashKeySerializer(new StringRedisSerializer());
        tpl.setHashValueSerializer(jsonSerializer());
        tpl.afterPropertiesSet();
        return tpl;
    }
}
