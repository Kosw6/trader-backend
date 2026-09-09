package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.RealtimeEnvelope;

public record ReliableOutboxCommittedEvent(
        String eventId,
        RealtimeEnvelope envelope
) {
}
