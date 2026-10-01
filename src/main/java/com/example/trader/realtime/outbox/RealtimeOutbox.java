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
                @Index(name = "idx_realtime_outbox_status_lease", columnList = "status, lease_until"),
                @Index(name = "idx_realtime_outbox_due", columnList = "status, next_attempt_at, createdAt"),
                @Index(name = "idx_realtime_outbox_deadline", columnList = "status, retry_deadline_at"),
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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String envelopeJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    /** 최초 시도 포함 누적 선점 수. Kafka 복구 시에도 초기화하지 않는다. */
    @Column(nullable = false)
    private long totalAttemptCount;

    private LocalDateTime firstAttemptAt;
    private LocalDateTime retryDeadlineAt;
    private LocalDateTime nextAttemptAt;

    @Column(length = 50)
    private String lastFailureCode;

    @Column(nullable = false)
    private boolean kafkaRecoveryPending;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastTriedAt;
    private LocalDateTime sentAt;

    /** 현재 PUBLISHING 선점 시도에만 유효한 fencing token. */
    @Column(name = "claim_token", length = 36)
    private String claimToken;

    /** 운영 추적용 선점 서버 식별자. 상태 변경 권한은 claimToken으로 검증한다. */
    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    /** 이 시각이 지나면 다른 작업자가 stale claim을 회수할 수 있다. */
    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

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

}
