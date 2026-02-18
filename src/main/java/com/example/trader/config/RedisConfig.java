package com.example.trader.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    /* ===== 공통 직렬화기 ===== */
    private GenericJackson2JsonRedisSerializer jsonSerializer(){
        PolymorphicTypeValidator ptv = LaissezFaireSubTypeValidator.instance;
        ObjectMapper om = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY) // ✅ 타입정보
                .build();
        return new GenericJackson2JsonRedisSerializer(om);
    }
    private RedisSerializationContext.SerializationPair<Object> jsonPair() {
        return RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer());
    }

    /** Redis가 살아있으면 RedisCacheManager, 아니면 ConcurrentMapCacheManager로 폴백 */
    @Bean
    public CacheManager cacheManager(ObjectProvider<RedisConnectionFactory> cfProvider) {
        RedisConnectionFactory cf = cfProvider.getIfAvailable();

        if (cf != null && redisReachable(cf)) {
            // 기본/캐시별 TTL & 직렬화
            RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                    .prefixCacheNameWith("trader::")
                    .disableCachingNullValues()
                    .serializeValuesWith(jsonPair())
                    .entryTtl(Duration.ofMinutes(10));

            Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
            perCache.put("stockAfter", defaults.entryTtl(Duration.ofMinutes(5)));
            perCache.put("fastCache",  defaults.entryTtl(Duration.ofMinutes(1)));
            perCache.put("slowCache",  defaults.entryTtl(Duration.ofHours(1)));

//            log.info("Using RedisCacheManager (Redis reachable).");
            return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(cf))
                    .cacheDefaults(defaults)
                    .withInitialCacheConfigurations(perCache)
                    .build();
        }

        log.warn("Redis unreachable → Falling back to ConcurrentMapCacheManager.");
        return new ConcurrentMapCacheManager();
    }

    private boolean redisReachable(RedisConnectionFactory cf) {
        try (RedisConnection c = cf.getConnection()) {
            String pong = c.ping();
            return pong != null && pong.equalsIgnoreCase("PONG");
        } catch (Exception e) {
            log.warn("Redis ping failed: {}", e.toString());
            return false;
        }
    }

    /** 런타임 중 Redis 에러가 나도 비즈니스 계속 진행하도록 경고만 찍고 무시 */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            public void handleCacheGetError(RuntimeException e, org.springframework.cache.Cache cache, Object key) {
                log.warn("Cache GET error cache={}, key={}: {}", n(cache), key, e.toString());
            }
            public void handleCachePutError(RuntimeException e, org.springframework.cache.Cache cache, Object key, Object value) {
                log.warn("Cache PUT error cache={}, key={}: {}", n(cache), key, e.toString());
            }
            public void handleCacheEvictError(RuntimeException e, org.springframework.cache.Cache cache, Object key) {
                log.warn("Cache EVICT error cache={}, key={}: {}", n(cache), key, e.toString());
            }
            public void handleCacheClearError(RuntimeException e, org.springframework.cache.Cache cache) {
                log.warn("Cache CLEAR error cache={}: {}", n(cache), e.toString());
            }
            private String n(org.springframework.cache.Cache c){ return c!=null? c.getName():"unknown"; }
        };
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
