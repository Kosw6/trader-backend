package com.example.trader.realtime.message;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class RealtimeEnvelope {

    private String eventId;

    private RealtimeType type;
    private RealtimeSubType subType;

    private Long teamId;
    private Long graphId;

    private Object payload;

    public RealtimeEnvelope ensureEventId() {
        if (this.eventId != null && !this.eventId.isBlank()) {
            return this;
        }

        return this.toBuilder()
                .eventId(java.util.UUID.randomUUID().toString())
                .build();
    }
}