package com.example.trader.realtime.message;

public enum RealtimeType {
    // DB Outbox와 Kafka를 통해 복구 가능한 내구성 업무 이벤트
    RELIABLE,
    // 순서대로 전달하지만 재생하지 않는 락·편집 세션 제어 이벤트
    TRANSIENT,
    // 최신 값만 중요하며 중간 유실을 허용하는 커서·드래그 이벤트
    VOLATILE
}
