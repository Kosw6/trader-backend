package com.example.trader.realtime.message;

//세부 이벤트 타입
public enum RealtimeSubType {
    NODE_CREATED,
    NODE_UPDATED,
    NODE_DELETED,
    CURSOR,
    DRAG_PREVIEW,
    CONTROL,
    LOCK_ACQUIRE,
    LOCK_ACQUIRED,
    LOCK_DENIED,
    LOCK_RELEASE,
    LOCK_RELEASED,
    LOCK_KEEPALIVE,
    EDIT_START,
    EDIT_END
}