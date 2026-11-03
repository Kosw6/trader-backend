package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Invalidation only: clients reload the authoritative, permission-checked tree. */
@Service
@RequiredArgsConstructor
public class TeamTreeChangeStager {
    private final TransactionalReliableEventService events;

    @Transactional(propagation = Propagation.MANDATORY)
    public void stage(Long teamId) {
        if (teamId == null || teamId <= 0) throw new IllegalArgumentException("teamId required");
        events.stage(RealtimeEnvelope.builder()
                .type(RealtimeType.RELIABLE).subType(RealtimeSubType.TEAM_TREE_CHANGED)
                .teamId(teamId).graphId(null).payload(Map.of("scope", "TEAM_TREE")).build());
    }
}
