package com.example.trader.realtime.inbound;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.snapshot.TransientRoomStateStore;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 순서를 유지하되 Outbox/Kafka 재생 대상이 아닌 실시간 제어 이벤트를 로컬에 전달한다. */
@Component
@RequiredArgsConstructor
public class TransientInboundHandler {

    private final RawPresenceBroadcaster broadcaster;
    private final TransientRoomStateStore transientStateStore;

    public void handle(RealtimeEnvelope envelope) {
        transientStateStore.observe(envelope);
        String roomKey = "team:%d:graph:%d".formatted(
                envelope.getTeamId(),
                envelope.getGraphId()
        );
        broadcaster.enqueueTransient(roomKey, envelope);
    }
}
