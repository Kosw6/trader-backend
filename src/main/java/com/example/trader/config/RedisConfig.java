package com.example.trader.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.*;

@Configuration
@EnableCaching
public class RedisConfig {

    // 공통 직렬화기: 키=문자열, 값=JSON
    private RedisSerializationContext.SerializationPair<Object> json() {
        return RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer()
        );
    }

    /** Redis 캐시매니저 (기본 TTL + 캐시별 TTL 설정) */
    @Bean
    public org.springframework.data.redis.cache.RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        var defaultConfig = org.springframework.data.redis.cache.RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(json())
                .prefixCacheNameWith("trader::");

        // 캐시 이름별 TTL 커스터마이즈 (예: 1분/1시간)
        var perCache = Map.of(
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
        tpl.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        tpl.setHashKeySerializer(new StringRedisSerializer());
        tpl.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        tpl.afterPropertiesSet();
        return tpl;
    }
}
