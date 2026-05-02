package com.example.trader.realtime.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EditEventPayload {

    private Long nodeId;
    private Long userId;

    private Integer baseVersion;
    private List<String> fields;

    private Long timestamp;
}
