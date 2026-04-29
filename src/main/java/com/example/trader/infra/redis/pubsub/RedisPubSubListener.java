package com.example.trader.infra.redis.pubsub;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeLatestKeyResolver;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "realtime.redis.pubsub-enabled",
        havingValue = "true"
)
public class RedisPubSubListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RawPresenceBroadcaster rawPresenceBroadcaster;
    private final RealtimeLatestKeyResolver latestKeyResolver;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);

            RealtimeEnvelope envelope =
                    objectMapper.readValue(body, RealtimeEnvelope.class);

            RawCursorMessage msg = objectMapper.convertValue(
                    envelope.getPayload(),
                    RawCursorMessage.class
            );

            String roomKey = "team:%d:graph:%d".formatted(
                    envelope.getTeamId(),
                    envelope.getGraphId()
            );

            if (envelope.getType() == RealtimeType.RELIABLE) {
                rawPresenceBroadcaster.publishReliable(roomKey, msg);
                return;
            }

            String latestKey = latestKeyResolver.resolve(msg);
            rawPresenceBroadcaster.publishLatest(roomKey, latestKey, msg);

        } catch (Exception e) {
            throw new IllegalStateException("Redis Pub/Sub message handling failed", e);
        }
    }
}