package com.example.trader.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/** 검색 전용 발행 경로. 실시간 Envelope 계약/3분 만료 정책을 재사용하지 않는다. */
@Slf4j
@Component
@ConditionalOnProperty(name="ai.search-relay.enabled", havingValue="true")
public class SearchOutboxRelay {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final KafkaProducer<String,String> producer;
    private final String topic;

    public SearchOutboxRelay(JdbcTemplate jdbc, ObjectMapper mapper, KafkaProperties properties,
            @Value("${ai.search-relay.topic:trader-search-source-v1}") String topic) {
        this.jdbc=jdbc; this.mapper=mapper; this.topic=topic;
        Map<String,Object> config = new HashMap<>(properties.buildProducerProperties(null));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG,"all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,true);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,3000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,15000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,5000);
        producer=new KafkaProducer<>(config);
    }

    @Scheduled(fixedDelayString="${ai.search-relay.delay-ms:1000}")
    public void relay() {
        // 한 건씩 선점하므로 배치 뒤쪽이 발행을 기다리다 lease를 잃지 않는다.
        for (int i=0; i<20 && !Thread.currentThread().isInterrupted(); i++) {
            UUID token=UUID.randomUUID();
            List<Map<String,Object>> rows=jdbc.queryForList("""
                WITH candidate AS (
                  SELECT event_id FROM search_source_outbox
                  WHERE published_at IS NULL AND next_attempt_at<=clock_timestamp()
                    AND (claim_until IS NULL OR claim_until<clock_timestamp())
                  ORDER BY occurred_at,event_id FOR UPDATE SKIP LOCKED LIMIT 1
                ) UPDATE search_source_outbox o SET claim_token=?,
                  claim_until=clock_timestamp()+interval '30 seconds',
                  publish_attempts=publish_attempts+1
                FROM candidate c WHERE o.event_id=c.event_id RETURNING o.*
                """,token);
            if(rows.isEmpty()) return;
            Map<String,Object> row=rows.get(0);
            try {
                Map<String,Object> event=new LinkedHashMap<>();
                event.put("schemaVersion",1);
                event.put("eventId",row.get("event_id").toString());
                event.put("sourceType",row.get("source_type"));
                event.put("sourceId",row.get("source_id").toString());
                event.put("sourceRevision",row.get("source_revision").toString());
                event.put("changeKinds",((java.sql.Array)row.get("change_kinds")).getArray());
                event.put("occurredAt",((java.sql.Timestamp)row.get("occurred_at")).toInstant().toString());
                String key=row.get("source_type")+":"+row.get("source_id");
                producer.send(new ProducerRecord<>(topic,key,mapper.writeValueAsString(event)))
                        .get(18,TimeUnit.SECONDS);
                // ACK 후 DB 실패는 재발행 대상이다. 같은 eventId로 수신 멱등성을 유지한다.
                jdbc.update("""
                    UPDATE search_source_outbox SET published_at=clock_timestamp(),
                      claim_token=NULL,claim_until=NULL,last_error_code=NULL
                    WHERE event_id=? AND claim_token=? AND claim_until>clock_timestamp()
                    """,row.get("event_id"),token);
            } catch(Exception e) {
                if(e instanceof InterruptedException) Thread.currentThread().interrupt();
                log.warn("검색 이벤트 발행/완료 실패 eventId={} type={}",
                        row.get("event_id"),e.getClass().getSimpleName());
                // 예외 원문에는 설정/주소가 들어갈 수 있어 안전한 분류만 저장한다.
                jdbc.update("""
                    UPDATE search_source_outbox SET claim_token=NULL,claim_until=NULL,
                      next_attempt_at=clock_timestamp()+interval '10 seconds',
                      last_error_code='PUBLISH_OR_ACK_RECORD_FAILED'
                    WHERE event_id=? AND claim_token=?
                    """,row.get("event_id"),token);
                return; // 공통 장애에서 배치 전체를 연속 호출하지 않는다.
            }
        }
    }

    @PreDestroy
    public void close() { producer.close(Duration.ofSeconds(3)); }
}
