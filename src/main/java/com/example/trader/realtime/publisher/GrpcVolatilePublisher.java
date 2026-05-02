package com.example.trader.realtime.publisher;

import com.example.trader.realtime.ReliablePublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.grpc.enabled", havingValue = "true")
public class GrpcVolatilePublisher implements ReliablePublisher.VolatilePublisher {

    private final RealtimeCommandGrpcClient grpcClient;

    @Override
    public void publish(RealtimeEnvelope envelope) {
        try {
            grpcClient.publish(envelope);
        } catch (Exception e) {
            log.debug("[VOLATILE-gRPC] publish skipped. eventId={}, reason={}",
                    envelope.getEventId(), e.getMessage());
        }
    }
}