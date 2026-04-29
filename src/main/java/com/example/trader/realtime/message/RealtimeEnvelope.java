package com.example.trader.realtime.message;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RealtimeEnvelope {

    private RealtimeType type;
    private RealtimeSubType subType;

    private Long teamId;
    private Long graphId;

    private Object payload;
}