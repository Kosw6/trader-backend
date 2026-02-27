package com.example.trader.ws.common;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
public class RoomPresenceCoalescer<T> {//메시지를 바로 보내지 않고 모아두는 버퍼 레이어, 실제 전송은 각각의 flusher가 진행

    /** 휘발성 이벤트: key별 마지막 값만 유지 */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, T>> latestByRoom = new ConcurrentHashMap<>();
    /** 신뢰 전송 이벤트(조작 이벤트, 노드 수정등): drop 금지 ->큐로 값 저장*/
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<T>> reliableQueueByRoom = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler;
    private final long flushIntervalMs;
    private final int maxReliableDrainPerFlush;

    /**
     * @param flushIntervalMs         ex) 100ms => 10Hz 전파
     * @param maxReliableDrainPerFlush reliable 이벤트를 flush에서 너무 오래 보내지 않도록 상한
     */
    public RoomPresenceCoalescer(long flushIntervalMs, int maxReliableDrainPerFlush) {
        this.flushIntervalMs = flushIntervalMs;
        this.maxReliableDrainPerFlush = maxReliableDrainPerFlush;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "presence-flusher");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(this::flushAllSafe, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    /** 휘발성: key별 해당 센더의 최신 메세지를 덮어씀 */
    public void publishLatest(String roomKey, String key, T msg) {
        latestByRoom.computeIfAbsent(roomKey, rk -> new ConcurrentHashMap<>()).put(key, msg);
    }

    /** 신뢰(하나씩 추가함 순서보장): enqueue */
    public void publishReliable(String roomKey, T msg) {
        reliableQueueByRoom.computeIfAbsent(roomKey, rk -> new ConcurrentLinkedQueue<>()).add(msg);
    }

    /** room 제거/정리 (옵션) */
    public void clearRoom(String roomKey) {
        latestByRoom.remove(roomKey);
        reliableQueueByRoom.remove(roomKey);
    }

    /** flush 시 외부가 실제 전송을 수행하도록 콜백 */
    public void flushRoom(String roomKey, Consumer<T> sender) {
        // 1) reliable 먼저 (제어 메시지 등)
        ConcurrentLinkedQueue<T> rq = reliableQueueByRoom.get(roomKey);
        if (rq != null) {
            for (int i = 0; i < maxReliableDrainPerFlush; i++) {
                T m = rq.poll();
                if (m == null) break;
                sender.accept(m);
            }
        }

        // 2) latest (cursor/drag) — key별 마지막 1개만
        ConcurrentHashMap<String, T> latestMap = latestByRoom.get(roomKey);
        if (latestMap != null && !latestMap.isEmpty()) {
            // 이번 flush에서 보낼 스냅샷만 뜨고, 맵은 유지(다음 overwrite 계속 받음)
            for (T m : latestMap.values()) {
                sender.accept(m);
            }
        }
    }

    private void flushAllSafe() {
        try {
            // latest 기준으로 방 키를 수집 (reliable만 있는 방도 있으니 둘 다 합침)
            Set<String> roomKeys = new HashSet<>();
            roomKeys.addAll(latestByRoom.keySet());
            roomKeys.addAll(reliableQueueByRoom.keySet());

            // 실제 전송은 “외부”가 flushRoom(roomKey, sender) 호출해서 수행
            // 여기서는 자동 flushAll을 하지 않음(전송 대상이 RAW/STOMP로 갈리므로).
            // -> RAW/STOMP 각각이 자신 방식으로 flushRoom 호출하도록 구성할 것.
        } catch (Exception e) {
            log.warn("[presence] flushAllSafe error: {}", e.toString());
        }
    }
}
