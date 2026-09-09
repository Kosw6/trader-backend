package com.example.trader.realtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalReliableEventDeduplicatorTest {

    @Test
    void claimsSameEventOnlyOnce() {
        LocalReliableEventDeduplicator deduplicator = deduplicator();

        assertThat(deduplicator.tryClaim("event-1")).isTrue();
        assertThat(deduplicator.tryClaim("event-1")).isFalse();
    }

    @Test
    void releasedEventCanBeClaimedAgain() {
        LocalReliableEventDeduplicator deduplicator = deduplicator();
        deduplicator.tryClaim("event-1");

        deduplicator.release("event-1");

        assertThat(deduplicator.tryClaim("event-1")).isTrue();
    }

    @Test
    void rejectsBlankReliableEventId() {
        LocalReliableEventDeduplicator deduplicator = deduplicator();

        assertThatThrownBy(() -> deduplicator.tryClaim(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("RELIABLE eventId must not be blank");
    }

    private LocalReliableEventDeduplicator deduplicator() {
        return new LocalReliableEventDeduplicator(100, Duration.ofMinutes(10));
    }
}
