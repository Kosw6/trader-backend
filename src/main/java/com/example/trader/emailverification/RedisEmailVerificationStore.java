package com.example.trader.emailverification;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisEmailVerificationStore implements EmailVerificationStore {
    // All keys share a hash slot: scripts remain atomic on Redis Cluster as well.
    static final String PREFIX = "trader:{signup-email}:";
    private static final DefaultRedisScript<Long> ISSUE = script("issue");
    private static final DefaultRedisScript<Long> CONFIRM = script("confirm");
    private static final DefaultRedisScript<Long> CONSUME = script("consume");
    private static final DefaultRedisScript<Long> CANCEL = script("cancel");
    private final StringRedisTemplate redis;

    public RedisEmailVerificationStore(StringRedisTemplate redis) { this.redis = redis; }

    private static DefaultRedisScript<Long> script(String name) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource("emailverification/" + name + ".lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Override public boolean issue(String emailHash, String clientHash, String challengeId, String codeMac) {
        return Long.valueOf(1).equals(redis.execute(ISSUE, List.of(PREFIX + "state:" + emailHash,
                PREFIX + "cooldown:" + emailHash, PREFIX + "email-limit:" + emailHash,
                PREFIX + "client-limit:" + clientHash, PREFIX + "global-limit"), challengeId, codeMac));
    }
    @Override public boolean confirm(String emailHash, String challengeId, String codeMac, String proofHash) {
        return Long.valueOf(1).equals(redis.execute(CONFIRM, List.of(PREFIX + "state:" + emailHash),
                challengeId, codeMac, proofHash));
    }
    @Override public boolean consume(String emailHash, String proofHash) {
        return Long.valueOf(1).equals(redis.execute(CONSUME, List.of(PREFIX + "state:" + emailHash), proofHash));
    }
    @Override public void cancel(String emailHash, String challengeId) {
        redis.execute(CANCEL, List.of(PREFIX + "state:" + emailHash), challengeId);
    }
}
