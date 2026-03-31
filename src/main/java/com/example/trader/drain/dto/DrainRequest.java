package com.example.trader.drain.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DrainRequest {
    private String reason;
    private long gracePeriodMillis;
}
