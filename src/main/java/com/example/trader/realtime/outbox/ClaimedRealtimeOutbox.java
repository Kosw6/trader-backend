package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.RealtimeSubType;

import java.util.Objects;

/**
 * 한 번의 Outbox 선점 결과.
 *
 * <p>claimToken은 이 작업의 완료·실패 상태를 변경할 수 있는 일회성 소유권이다.
 */
public record ClaimedRealtimeOutbox(
        String eventId,
        String claimToken,
        Long graphId,
        RealtimeSubType subType,
        String envelopeJson
) {
    public ClaimedRealtimeOutbox {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(envelopeJson, "envelopeJson");
    }

    public static ClaimedRealtimeOutbox from(RealtimeOutbox outbox) {
        return new ClaimedRealtimeOutbox(
                outbox.getEventId(),
                outbox.getClaimToken(),
                outbox.getGraphId(),
                outbox.getSubType(),
                outbox.getEnvelopeJson()
        );
    }
}
