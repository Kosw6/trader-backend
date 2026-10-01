package com.example.trader.service;

import com.example.trader.entity.NodeHistory;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.outbox.TransactionalReliableEventService;
import com.example.trader.repository.NodeHistoryRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamNodePositionWriterTest {

    private static final Long TEAM_ID = 1L;
    private static final Long GRAPH_ID = 2L;
    private static final Long NODE_ID = 3L;
    private static final Long USER_ID = 4L;

    @Mock NodeRepository nodeRepository;
    @Mock NodeHistoryRepository nodeHistoryRepository;
    @Mock TransactionalReliableEventService reliableEventService;

    TeamNodePositionWriter writer;

    @BeforeEach
    void setUp() {
        writer = new TeamNodePositionWriter(
                nodeRepository,
                nodeHistoryRepository,
                reliableEventService,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("CAS 성공 시 위치 버전과 x,y 이력 및 RELIABLE 이벤트를 함께 생성")
    void write_position_history_and_outbox_after_successful_cas() {
        when(nodeRepository.updatePositionInTeamIfVersion(
                TEAM_ID, GRAPH_ID, NODE_ID, 4, 10.0, 20.0
        )).thenReturn(1);

        Optional<Integer> result = writer.write(
                TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 4, 10.0, 20.0
        );

        assertThat(result).contains(5);

        ArgumentCaptor<NodeHistory> historyCaptor = ArgumentCaptor.forClass(NodeHistory.class);
        verify(nodeHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getVersion()).isEqualTo(5);
        assertThat(historyCaptor.getValue().getChangedBy()).isEqualTo(USER_ID);
        assertThat(historyCaptor.getValue().getChangedFields()).isEqualTo("[\"x\",\"y\"]");

        ArgumentCaptor<RealtimeEnvelope> envelopeCaptor = ArgumentCaptor.forClass(RealtimeEnvelope.class);
        verify(reliableEventService).stage(envelopeCaptor.capture());
        RealtimeEnvelope envelope = envelopeCaptor.getValue();
        assertThat(envelope.getSubType()).isEqualTo(RealtimeSubType.NODE_POSITION_UPDATED);
        assertThat(envelope.getPayload()).isInstanceOf(RawCursorMessage.class);
        RawCursorMessage message = (RawCursorMessage) envelope.getPayload();
        assertThat(message.version()).isEqualTo(5);
        assertThat(message.baseVersion()).isNull();
        assertThat(message.fields()).containsExactly("x", "y");
    }

    @Test
    @DisplayName("CAS 실패 시 이력과 Outbox를 생성하지 않음")
    void do_not_create_side_effects_when_cas_fails() {
        when(nodeRepository.updatePositionInTeamIfVersion(
                TEAM_ID, GRAPH_ID, NODE_ID, 4, 10.0, 20.0
        )).thenReturn(0);

        assertThat(writer.write(
                TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 4, 10.0, 20.0
        )).isEmpty();

        verifyNoInteractions(nodeHistoryRepository, reliableEventService);
    }
}
