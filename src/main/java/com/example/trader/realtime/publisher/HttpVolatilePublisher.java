package com.example.trader.realtime.publisher;

import com.example.trader.realtime.ReliablePublisher;
import com.example.trader.realtime.VolatilePublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.http.enabled", havingValue = "true")
public class HttpVolatilePublisher{

    private final RestClient restClient;

    @Value("${realtime.http.target:http://localhost:8081}")
    private String target;


    public void publish(RealtimeEnvelope envelope) {
        try {
            restClient.post()
                    .uri(target + "/internal/realtime/events")
                    .body(envelope)
                    .retrieve()
                    .toBodilessEntity();

        } catch (Exception e) {
            log.debug("[VOLATILE-HTTP] publish skipped. eventId={}, reason={}",
                    envelope.getEventId(), e.getMessage());
        }
    }
}