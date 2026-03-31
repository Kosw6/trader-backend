package com.example.trader.recovery;

import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Component
public class ReplayStateTracker {

    private final AtomicInteger replayCount = new AtomicInteger(0);

    /**
     * partition -> catch-up 시작 시점의 마지막으로 따라잡아야 할 offset
     * latest end offset이 15면 실제 마지막 메시지 offset은 14
     */
    private final Map<Integer, Long> targetOffsets = new ConcurrentHashMap<>();

    /**
     * partition -> 마지막으로 consume한 offset
     */
    private final Map<Integer, Long> lastConsumedOffsets = new ConcurrentHashMap<>();

    /**
     * catch-up 완료 처리 중복 실행 방지
     */
    private final AtomicBoolean catchupCompleted = new AtomicBoolean(false);

    public void reset() {
        replayCount.set(0);
        targetOffsets.clear();
        lastConsumedOffsets.clear();
        catchupCompleted.set(false);
    }

    /**
     * AdminClient latest end offset(exclusive)을 받아
     * 실제 replay 목표 offset(end - 1)으로 저장
     */
    public void setTargetOffsets(Map<Integer, Long> endOffsetsExclusive) {
        targetOffsets.clear();

        if (endOffsetsExclusive == null || endOffsetsExclusive.isEmpty()) {
            return;
        }

        for (Map.Entry<Integer, Long> entry : endOffsetsExclusive.entrySet()) {
            int partition = entry.getKey();
            long endOffsetExclusive = entry.getValue() == null ? 0L : entry.getValue();

            long targetOffset = endOffsetExclusive - 1;
            if (targetOffset >= 0) {
                targetOffsets.put(partition, targetOffset);
            }
        }
    }

    public void onReplay(int partition, long offset) {
        replayCount.incrementAndGet();
        lastConsumedOffsets.put(partition, offset);
    }

    public boolean hasBacklog() {
        return !targetOffsets.isEmpty();
    }

    public boolean isCatchupCompleted() {
        if (targetOffsets.isEmpty()) {
            return true;
        }

        for (Map.Entry<Integer, Long> entry : targetOffsets.entrySet()) {
            int partition = entry.getKey();
            long targetOffset = entry.getValue();
            long consumedOffset = lastConsumedOffsets.getOrDefault(partition, -1L);

            if (consumedOffset < targetOffset) {
                return false;
            }
        }
        return true;
    }

    public boolean markCompletedOnce() {
        return catchupCompleted.compareAndSet(false, true);
    }

    public String summary() {
        return "replayCount=" + replayCount.get()
                + ", targetOffsets=" + targetOffsets
                + ", lastConsumedOffsets=" + lastConsumedOffsets
                + ", catchupCompleted=" + catchupCompleted.get();
    }
}