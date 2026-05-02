package com.example.trader.realtime.publisher;

import com.example.trader.realtime.grpc.PublishRealtimeEventRequest;
import com.example.trader.realtime.grpc.PublishRealtimeEventResponse;
import com.example.trader.realtime.grpc.RealtimeCommandServiceGrpc;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RealtimeCommandGrpcClient {

    private final ObjectMapper objectMapper;

    @GrpcClient("realtime-command")
    private RealtimeCommandServiceGrpc.RealtimeCommandServiceBlockingStub stub;

    public void publish(RealtimeEnvelope envelope) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(envelope.getPayload());

        PublishRealtimeEventRequest request =
                PublishRealtimeEventRequest.newBuilder()
                        .setEventId(nullToEmpty(envelope.getEventId()))
                        .setType(envelope.getType() != null ? envelope.getType().name() : "")
                        .setSubType(envelope.getSubType() != null ? envelope.getSubType().name() : "")
                        .setTeamId(envelope.getTeamId() != null ? envelope.getTeamId() : 0L)
                        .setGraphId(envelope.getGraphId() != null ? envelope.getGraphId() : 0L)
                        .setPayloadJson(payloadJson)
                        .setCreatedAtEpochMs(System.currentTimeMillis())
                        .build();

        PublishRealtimeEventResponse response =
                stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                        .publishRealtimeEvent(request);

        if (!response.getAccepted()) {
            throw new IllegalStateException(
                    "gRPC publish rejected. mode=" + response.getMode()
                            + ", reason=" + response.getReason()
            );
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
