package com.example.trader.realtime;

import com.example.trader.realtime.message.RealtimeEnvelope;

public interface ReliablePublisher {
    void publish(RealtimeEnvelope envelope);

    default void publishOrThrow(RealtimeEnvelope envelope) throws Exception {
        publish(envelope);
    }

    interface VolatilePublisher {
        void publish(RealtimeEnvelope envelope);
    }
}
