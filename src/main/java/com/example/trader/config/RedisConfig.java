package com.example.trader.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.trader.dto.map.PageNodesCacheDto;
import com.example.trader.dto.map.ResponseGraphDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    /** 공통 ObjectMapper - default typing 없음 */
    @Bean
    public ObjectMapper redisObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    /** 캐시별 serializer pair 생성 */
    private RedisSerializationContext.SerializationPair<Object> pair(
            ObjectMapper om, Class<?> type
    ) {
        Jackson2JsonRedisSerializer<?> serializer = new Jackson2JsonRedisSerializer<>(om, type);
        @SuppressWarnings("unchecked")
        RedisSerializationContext.SerializationPair<Object> pair =
                (RedisSerializationContext.SerializationPair<Object>)
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                (Jackson2JsonRedisSerializer<Object>) serializer
                        );
        return pair;
    }

    @Bean
    public CacheManager cacheManager(
            ObjectProvider<RedisConnectionFactory> cfProvider,
            ObjectMapper redisObjectMapper
    ) {
        RedisConnectionFactory cf = cfProvider.getIfAvailable();

        if (cf != null && redisReachable(cf)) {
            RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                    .prefixCacheNameWith("trader::")
                    .disableCachingNullValues()
                    .entryTtl(Duration.ofMinutes(10));

            Map<String, RedisCacheConfiguration> perCache = new HashMap<>();

            perCache.put("graphDetail",
                    defaultConfig
                            .entryTtl(Duration.ofMinutes(10))
                            .serializeValuesWith(pair(redisObjectMapper, ResponseGraphDto.class))
            );

            perCache.put("pageNodes",
                    defaultConfig
                            .entryTtl(Duration.ofMinutes(30))
                            .serializeValuesWith(pair(redisObjectMapper, PageNodesCacheDto.class))
            );


            return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(cf))
                    .cacheDefaults(defaultConfig)
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
            private String n(org.springframework.cache.Cache c){ return c != null ? c.getName() : "unknown"; }
        };
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
        var tpl = new StringRedisTemplate(cf);
        tpl.setEnableDefaultSerializer(false);
        return tpl;
    }

    /**
     * 주의:
     * RedisTemplate<String, Object> 를 유지하면 다시 Object 역직렬화 문제가 생길 수 있음
     * 가능하면 typed RedisTemplate 또는 별도 serializer를 쓰는 게 안전
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory cf,
            ObjectMapper redisObjectMapper
    ) {
        var tpl = new RedisTemplate<String, Object>();
        tpl.setConnectionFactory(cf);
        tpl.setKeySerializer(new StringRedisSerializer());

        Jackson2JsonRedisSerializer<Object> valueSerializer =
                new Jackson2JsonRedisSerializer<>(redisObjectMapper, Object.class);

        tpl.setValueSerializer(valueSerializer);
        tpl.setHashKeySerializer(new StringRedisSerializer());
        tpl.setHashValueSerializer(valueSerializer);
        tpl.afterPropertiesSet();
        return tpl;
    }
}