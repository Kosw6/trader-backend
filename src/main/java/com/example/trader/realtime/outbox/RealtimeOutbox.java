package com.example.trader.realtime.outbox;

import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "realtime_outbox",
        indexes = {
                @Index(name = "idx_realtime_outbox_status_created", columnList = "status, createdAt"),
                @Index(name = "idx_realtime_outbox_graph_id", columnList = "graphId")
        }
)
public class RealtimeOutbox {

    @Id
    @Column(length = 36)
    private String eventId;

    private Long teamId;
    private Long graphId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RealtimeType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private RealtimeSubType subType;

    @Lob
    @Column(nullable = false)
    private String envelopeJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastTriedAt;
    private LocalDateTime sentAt;

    @Column(length = 1000)
    private String lastErrorMessage;

    public static RealtimeOutbox pending(
            RealtimeEnvelope envelope,
            ObjectMapper objectMapper
    ) {
        try {
            RealtimeEnvelope event = envelope.ensureEventId();

            RealtimeOutbox outbox = new RealtimeOutbox();
            outbox.eventId = event.getEventId();
            outbox.teamId = event.getTeamId();
            outbox.graphId = event.getGraphId();
            outbox.type = event.getType();
            outbox.subType = event.getSubType();
            outbox.envelopeJson = objectMapper.writeValueAsString(event);
            outbox.status = OutboxStatus.PENDING;
            outbox.retryCount = 0;
            outbox.createdAt = LocalDateTime.now();

            return outbox;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize realtime outbox event", e);
        }
    }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.lastTriedAt = LocalDateTime.now();
        this.lastErrorMessage = null;
    }

    public void markFailed(String reason) {
        this.status = OutboxStatus.FAILED;
        this.retryCount += 1;
        this.lastTriedAt = LocalDateTime.now();
        this.lastErrorMessage = reason == null
                ? null
                : reason.substring(0, Math.min(reason.length(), 1000));
    }

    public void markPendingForRetry() {
        this.status = OutboxStatus.PENDING;
        this.lastTriedAt = LocalDateTime.now();
    }
}