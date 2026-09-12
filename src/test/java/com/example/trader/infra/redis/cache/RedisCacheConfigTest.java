package com.example.trader.infra.redis.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisCacheConfigTest {

    @Test
    @DisplayName("Node 조회 캐시 두 종류의 TTL은 모두 15분")
    @SuppressWarnings("unchecked")
    void node_query_cache_ttl_is_fifteen_minutes() {
        ObjectProvider<RedisConnectionFactory> provider = mock(ObjectProvider.class);
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(provider.getIfAvailable()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        CacheManager cacheManager = new RedisCacheConfig().cacheManager(provider);

        RedisCacheManager redisCacheManager = (RedisCacheManager) cacheManager;
        redisCacheManager.initializeCaches();
        assertThat(redisCacheManager.getCacheConfigurations().get("graphDetail").getTtl())
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(redisCacheManager.getCacheConfigurations().get("pageNodes").getTtl())
                .isEqualTo(Duration.ofMinutes(15));
    }
}
