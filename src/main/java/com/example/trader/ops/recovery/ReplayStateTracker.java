package com.example.trader.ops.recovery;

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
    private final Map<Integer, Long> targetOffsets = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastConsumedOffsets = new ConcurrentHashMap<>();
    private final AtomicBoolean catchupCompleted = new AtomicBoolean(false);

    public void reset() {
        replayCount.set(0);
        targetOffsets.clear();
        lastConsumedOffsets.clear();
        catchupCompleted.set(false);
    }

    public void setTargetOffsets(Map<Integer, Long> endOffsetsExclusive) {
        targetOffsets.clear();

        if (endOffsetsExclusive == null || endOffsetsExclusive.isEmpty()) {
            return;
        }

        for (Map.Entry<Integer, Long> entry : endOffsetsExclusive.entrySet()) {
            long targetOffset = (entry.getValue() == null ? 0L : entry.getValue()) - 1;

            if (targetOffset >= 0) {
                targetOffsets.put(entry.getKey(), targetOffset);
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
            long consumedOffset = lastConsumedOffsets.getOrDefault(entry.getKey(), -1L);
            if (consumedOffset < entry.getValue()) {
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