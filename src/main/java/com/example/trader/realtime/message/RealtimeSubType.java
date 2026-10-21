package com.example.trader.realtime.message;

//세부 이벤트 타입
public enum RealtimeSubType {
    TEAM_TREE_CHANGED,
    CHAT_ROOM_CREATED,
    CHAT_MESSAGE_CREATED,
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
    EDIT_END,
    EDIT_CANCEL,
    TRANSIENT_SNAPSHOT,
    RELIABLE_RESYNC_REQUIRED,
    NODE_POSITION_UPDATED;

    /** Group-wide stream, independent of the currently selected canvas graph. */
    public boolean isTeamScoped() {
        return this == TEAM_TREE_CHANGED || this == CHAT_ROOM_CREATED || this == CHAT_MESSAGE_CREATED;
    }
}
