package com.example.trader.ws.raw.event;

import java.util.List;

public record NodeChangedEvent(
        Long teamId,
        Long graphId,
        Long nodeId,
        Long userId,
        String subType,
        List<String> fields,
        Integer version,
        Double x,
        Double y
) {}
