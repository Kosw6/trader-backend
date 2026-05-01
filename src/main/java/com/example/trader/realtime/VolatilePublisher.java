package com.example.trader.realtime;

import com.example.trader.realtime.message.RealtimeEnvelope;

public interface VolatilePublisher {
    void publish(RealtimeEnvelope envelope);
}
