package com.example.trader.realtime;

import com.example.trader.realtime.message.RealtimeEnvelope;

public interface ReliablePublisher {
    void publish(RealtimeEnvelope envelope);
}
