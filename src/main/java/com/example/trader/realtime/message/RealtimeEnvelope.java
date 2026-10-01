package com.example.trader.realtime.message;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class RealtimeEnvelope {

    private String eventId;

    /** 최초 발행 서버. Redis loopback에서 원본 서버의 중복 전파를 막는다. */
    private String originInstanceId;

    private RealtimeType type;
    private RealtimeSubType subType;

    private Long teamId;
    private Long graphId;

    private Object payload;

    /**
     * 이벤트 최초 발행 시점 (epoch millis).
     * RedisPubSubPublisher / KafkaPublisher에서 최초 발행 시 세팅.
     * Redis Pub/Sub listener / Kafka consumer에서 수신 시 lag 계산에 사용.
     * 구버전 메시지 호환성을 위해 null 허용.
     */
    private Long publishedAt;

    /** 방별 TRANSIENT 상태 변경 순서. Redis 장애로 전역 순서를 만들 수 없으면 null이다. */
    private Long transientRevision;

    public RealtimeEnvelope ensureEventId() {
        if (this.eventId != null && !this.eventId.isBlank()) {
            return this;
        }

        return this.toBuilder()
                .eventId(java.util.UUID.randomUUID().toString())
                .build();
    }

    public RealtimeEnvelope ensureOriginInstanceId(String instanceId) {
        if (this.originInstanceId != null && !this.originInstanceId.isBlank()) {
            return this;
        }
        return this.toBuilder()
                .originInstanceId(instanceId)
                .build();
    }
}
