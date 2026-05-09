package com.example.trader;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "app.mode=single",
        "realtime.kafka.enabled=false",
        "realtime.redis.pubsub-enabled=false",
        "realtime.grpc.enabled=false",
        "realtime.http.enabled=false"
})
class RedisRaceConditionTest {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void unlock_with_get_then_delete_can_remove_reacquired_lock() throws Exception {
        String key = "canvas:lock:1:1:100";

        String userA = "1";
        String userB = "2";

        redis.delete(key);

        redis.opsForValue().setIfAbsent(key, userA, Duration.ofMillis(300));

        CountDownLatch aReadDone = new CountDownLatch(1);

        Thread threadA = new Thread(() -> {
            String holder = redis.opsForValue().get(key);

            if (userA.equals(holder)) {
                aReadDone.countDown();

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                redis.delete(key);
            }
        });

        threadA.start();
        aReadDone.await();

        Thread.sleep(350);

        Boolean bAcquired = redis.opsForValue().setIfAbsent(key, userB, Duration.ofSeconds(5));
        assertThat(bAcquired).isTrue();

        threadA.join();

        String finalHolder = redis.opsForValue().get(key);

        assertThat(finalHolder).isNull();
    }

    @Test
    void lua_unlock_is_atomic_and_preserves_reacquired_lock() throws Exception {
        String key = "canvas:lock:1:1:100";

        String userA = "1";
        String userB = "2";

        redis.delete(key);

        redis.opsForValue().setIfAbsent(key, userA, Duration.ofMillis(300));

        CountDownLatch aReadDone = new CountDownLatch(1);

        Thread threadA = new Thread(() -> {
            String holder = redis.opsForValue().get(key);

            if (userA.equals(holder)) {
                aReadDone.countDown();

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                String unlockScript = """
                    if redis.call('GET', KEYS[1]) == ARGV[1] then
                        return redis.call('DEL', KEYS[1])
                    else
                        return 0
                    end
                    """;

                Long result = redis.execute(
                        new DefaultRedisScript<>(unlockScript, Long.class),
                        List.of(key),
                        userA
                );

                assertThat(result).isEqualTo(0L);
            }
        });

        threadA.start();
        aReadDone.await();

        Thread.sleep(350);

        Boolean bAcquired = redis.opsForValue().setIfAbsent(key, userB, Duration.ofSeconds(5));
        assertThat(bAcquired).isTrue();

        threadA.join();

        String finalHolder = redis.opsForValue().get(key);

        assertThat(finalHolder).isEqualTo(userB);
    }
}