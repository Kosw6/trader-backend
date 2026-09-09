package com.example.trader.ws.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RoomPresenceCoalescer<T> {

    // 휘발성 이벤트: key별 마지막 값만 유지
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, T>> latestByRoom = new ConcurrentHashMap<>();

    //room에 이번 tick에 보낼 변경이 있었는지 표시
    private final ConcurrentHashMap<String, AtomicBoolean> dirtyByRoom = new ConcurrentHashMap<>();


    public RoomPresenceCoalescer() {
    }

    // 휘발성: key별 해당 센더의 최신 메세지를 덮어씀
    public void publishLatest(String roomKey, String key, T msg) {
        latestByRoom.computeIfAbsent(roomKey, rk -> new ConcurrentHashMap<>()).put(key, msg);
        markDirty(roomKey);
    }

    private void markDirty(String roomKey) {
        dirtyByRoom.computeIfAbsent(roomKey, rk -> new AtomicBoolean(false)).set(true);
    }

    // room 제거/정리 (옵션)
    public void clearRoom(String roomKey) {
        latestByRoom.remove(roomKey);
        dirtyByRoom.remove(roomKey);
    }

    public Set<String> roomKeysSnapshot() {
        return Set.copyOf(latestByRoom.keySet());
    }

    /**
     * Dirty 기반 + DrainLatest
     * - dirty=false면 아무것도 안 보냄
     * - dirty=true면 latest를 "스냅샷 뜨면서 clear" (다음 tick 중복 전송 방지)
     * - dirty는 여기서 false로 원자적으로 내림
     */
    public Collection<T> drainLatestIfDirty(String roomKey) {
        AtomicBoolean dirty = dirtyByRoom.get(roomKey);
        if (dirty == null || !dirty.compareAndSet(true, false)) {
            return List.of();
        }

        var m = latestByRoom.get(roomKey);
        if (m == null || m.isEmpty()) return List.of();

        // snapshot + clear (drain)
        ArrayList<T> out = new ArrayList<>(m.values());
        m.clear();
        return out;
    }

    //latest 스냅샷 추출 (flush 시점에 broadcaster가 배치 생성에 사용)
    public Collection<T> snapshotLatest(String roomKey) {
        var m = latestByRoom.get(roomKey);
        if (m == null || m.isEmpty()) return List.of();
        return new ArrayList<>(m.values()); // snapshot
    }

}
