package com.example.trader.realtime.inbound;

import com.example.trader.realtime.ReliablePropagationDedup;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReliableInboundHandler {

    private final RawPresenceBroadcaster rawPresenceBroadcaster;
    private final ReliablePropagationDedup propagationDedup;

    /**
     * Redis Pub/Sub 수신 경로.
     * WS 브로드캐스트 후 이 서버가 해당 이벤트를 전파했음을 dedup key로 기록한다.
     * Kafka consumer가 동일 eventId 수신 시 dedup key 확인 → skip.
     */
    public void handle(RealtimeEnvelope envelope) {
        String roomKey = "team:%d:graph:%d".formatted(
                envelope.getTeamId(),
                envelope.getGraphId()
        );
        log.info("[RELIABLE-INBOUND] roomKey={}, eventId={}", roomKey, envelope.getEventId());
        rawPresenceBroadcaster.publishReliable(roomKey, envelope);

        // Pub/Sub 전파 완료 → dedup key 기록 (Kafka consumer skip 기준)
        propagationDedup.markPropagated(envelope.getEventId());
    }
}