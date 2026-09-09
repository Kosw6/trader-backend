package com.example.trader.realtime;

import com.example.trader.realtime.message.RealtimeEnvelope;

/**
 * DB 커밋과 내구성 저장을 요구하지 않는 실시간 메시지 전파 계약.
 * RELIABLE 이벤트는 TransactionalReliableEventService를 통해 Outbox에 기록해야 한다.
 */
public interface LiveRealtimePublisher {

    void publishTransient(RealtimeEnvelope envelope);

    void publishVolatile(RealtimeEnvelope envelope);
}
