package com.example.trader.realtime;

import com.example.trader.realtime.message.RealtimeEnvelope;

//KafkaPublisher vs LocalRealtimePublisher
//즉 멀티환경, 로컬 환경에서 분기 처리를 나누기 위해 인터페이스화
public interface RealtimePublisher {
    void publish(RealtimeEnvelope envelope);
}