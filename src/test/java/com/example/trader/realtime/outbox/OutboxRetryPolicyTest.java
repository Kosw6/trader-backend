package com.example.trader.realtime.outbox;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.assertThat;

class OutboxRetryPolicyTest {
    @Test
    void exponential_delay_jitter_cap_and_deadline_are_independent_of_counter_reset() {
        var now = LocalDateTime.of(2026, 9, 19, 10, 0);
        var deadline = now.plusMinutes(3);
        assertThat(OutboxRetryPolicy.nextAttempt(now, 1, deadline, 0)).isEqualTo(now.plusSeconds(5));
        assertThat(OutboxRetryPolicy.nextAttempt(now, 2, deadline, 0)).isEqualTo(now.plusSeconds(10));
        assertThat(OutboxRetryPolicy.nextAttempt(now, 3, deadline, 0)).isEqualTo(now.plusSeconds(20));
        assertThat(OutboxRetryPolicy.nextAttempt(now, 100, deadline, 0.5)).isEqualTo(now.plusSeconds(33));
        assertThat(OutboxRetryPolicy.nextAttempt(deadline.minusSeconds(1), 1, deadline, 0.5)).isEqualTo(deadline);
    }
}
