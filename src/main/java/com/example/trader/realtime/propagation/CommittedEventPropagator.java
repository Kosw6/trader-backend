package com.example.trader.realtime.propagation;

import com.example.trader.realtime.message.RealtimeEnvelope;

/**
 * DB에 커밋된 신뢰 이벤트를 현재 실행 모드의 실시간 경로로 전파한다.
 * Kafka 내구성 발행은 이 계약에 포함하지 않고 Outbox relay가 담당한다.
 */
public interface CommittedEventPropagator {

    void propagate(RealtimeEnvelope envelope);
}
