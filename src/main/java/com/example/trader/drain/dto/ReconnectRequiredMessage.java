package com.example.trader.drain.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReconnectRequiredMessage {
    private String type;
    private String reason;
    private long gracePeriodMillis;
}