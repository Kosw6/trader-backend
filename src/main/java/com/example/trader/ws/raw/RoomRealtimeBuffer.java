package com.example.trader.ws.raw;

import com.example.trader.realtime.message.RealtimeEnvelope;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 방별 RELIABLE/TRANSIENT 전달 버퍼.
 *
 * 큐가 포화되면 오래된 이벤트를 일부 버리는 대신 복구 신호를 별도 슬롯에 남긴다.
 * RELIABLE은 DB 재조회, TRANSIENT는 현재 상태 스냅샷으로 복구한다.
 */
public class RoomRealtimeBuffer {

    private final ConcurrentHashMap<String, RoomBuffer> rooms = new ConcurrentHashMap<>();
    private final int reliableCapacity;
    private final int transientCapacity;
    private final int maxDrainPerFlush;

    public RoomRealtimeBuffer(int reliableCapacity, int transientCapacity, int maxDrainPerFlush) {
        if (reliableCapacity <= 0 || transientCapacity <= 0 || maxDrainPerFlush <= 0) {
            throw new IllegalArgumentException("Realtime buffer limits must be greater than zero");
        }
        this.reliableCapacity = reliableCapacity;
        this.transientCapacity = transientCapacity;
        this.maxDrainPerFlush = maxDrainPerFlush;
    }

    public void offerReliable(String roomKey, RealtimeEnvelope event) {
        rooms.computeIfAbsent(roomKey, ignored -> new RoomBuffer())
                .offerReliable(event, reliableCapacity);
    }

    public void offerTransient(String roomKey, RealtimeEnvelope event) {
        rooms.computeIfAbsent(roomKey, ignored -> new RoomBuffer())
                .offerTransient(event, transientCapacity);
    }

    public Drain drain(String roomKey) {
        RoomBuffer room = rooms.get(roomKey);
        return room == null ? Drain.empty() : room.drain(maxDrainPerFlush);
    }

    public Set<String> roomKeysSnapshot() {
        return Set.copyOf(rooms.keySet());
    }

    public void clearRoom(String roomKey) {
        rooms.remove(roomKey);
    }

    public record Drain(
            boolean persistentResyncRequired,
            boolean transientSnapshotRequired,
            List<RealtimeEnvelope> reliableEvents,
            List<RealtimeEnvelope> transientEvents
    ) {
        private static Drain empty() {
            return new Drain(false, false, List.of(), List.of());
        }
    }

    private static final class RoomBuffer {
        private final ArrayDeque<RealtimeEnvelope> reliable = new ArrayDeque<>();
        private final ArrayDeque<RealtimeEnvelope> transientEvents = new ArrayDeque<>();
        private boolean persistentResyncRequired;
        private boolean transientSnapshotRequired;

        synchronized void offerReliable(RealtimeEnvelope event, int capacity) {
            if (persistentResyncRequired) return;
            if (reliable.size() >= capacity) {
                reliable.clear();
                persistentResyncRequired = true;
                return;
            }
            reliable.addLast(event);
        }

        synchronized void offerTransient(RealtimeEnvelope event, int capacity) {
            if (transientSnapshotRequired) return;
            if (transientEvents.size() >= capacity) {
                transientEvents.clear();
                transientSnapshotRequired = true;
                return;
            }
            transientEvents.addLast(event);
        }

        synchronized Drain drain(int maxPerQueue) {
            boolean resync = persistentResyncRequired;
            boolean snapshot = transientSnapshotRequired;
            persistentResyncRequired = false;
            transientSnapshotRequired = false;

            List<RealtimeEnvelope> reliableBatch = drain(reliable, maxPerQueue);
            List<RealtimeEnvelope> transientBatch = drain(transientEvents, maxPerQueue);
            return new Drain(resync, snapshot, reliableBatch, transientBatch);
        }

        private List<RealtimeEnvelope> drain(ArrayDeque<RealtimeEnvelope> source, int limit) {
            List<RealtimeEnvelope> result = new ArrayList<>(Math.min(source.size(), limit));
            for (int i = 0; i < limit; i++) {
                RealtimeEnvelope event = source.pollFirst();
                if (event == null) break;
                result.add(event);
            }
            return result;
        }
    }
}
