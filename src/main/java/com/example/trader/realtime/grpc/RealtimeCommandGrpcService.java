package com.example.trader.realtime.grpc;

import com.example.trader.realtime.inbound.ReliableInboundHandler;
import com.example.trader.realtime.inbound.VolatileInboundHandler;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Map;

@GrpcService
@RequiredArgsConstructor
public class RealtimeCommandGrpcService
        extends RealtimeCommandServiceGrpc.RealtimeCommandServiceImplBase {

    private final ObjectMapper objectMapper;
    private final ReliableInboundHandler reliableInboundHandler;
    private final VolatileInboundHandler volatileInboundHandler;
    private final MeterRegistry meterRegistry;

    @Override
    public void publishRealtimeEvent(
            PublishRealtimeEventRequest request,
            StreamObserver<PublishRealtimeEventResponse> responseObserver
    ) {
        try {
            Map payload = objectMapper.readValue(request.getPayloadJson(), Map.class);

            RealtimeEnvelope envelope = RealtimeEnvelope.builder()
                    .eventId(emptyToNull(request.getEventId()))
                    .type(RealtimeType.valueOf(request.getType()))
                    .subType(parseSubType(request.getSubType()))
                    .teamId(request.getTeamId() == 0L ? null : request.getTeamId())
                    .graphId(request.getGraphId() == 0L ? null : request.getGraphId())
                    .payload(payload)
                    .build();
            meterRegistry.counter(
                    "realtime.relay.inbound",
                    "path", "grpc",
                    "type", envelope.getType().name(),
                    "subType", envelope.getSubType() == null
                            ? "unknown"
                            : envelope.getSubType().name()
            ).increment();

            if (envelope.getType() == RealtimeType.RELIABLE) {
                reliableInboundHandler.handle(envelope);
            } else if (envelope.getType() == RealtimeType.VOLATILE) {
                volatileInboundHandler.handle(envelope);
            } else {
                throw new IllegalArgumentException("Unsupported realtime type: " + envelope.getType());
            }

            responseObserver.onNext(
                    PublishRealtimeEventResponse.newBuilder()
                            .setAccepted(true)
                            .setMode("GRPC")
                            .setReason("OK")
                            .build()
            );
            responseObserver.onCompleted();

        } catch (Exception e) {
            responseObserver.onNext(
                    PublishRealtimeEventResponse.newBuilder()
                            .setAccepted(false)
                            .setMode("GRPC")
                            .setReason(e.getMessage() == null ? "unknown" : e.getMessage())
                            .build()
            );
            responseObserver.onCompleted();
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private RealtimeSubType parseSubType(String value) {
        if (value == null || value.isBlank()) return null;
        return RealtimeSubType.valueOf(value);
    }
}