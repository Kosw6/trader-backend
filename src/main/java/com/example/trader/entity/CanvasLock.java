package com.example.trader.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Redis 장애 시 노드 락을 DB에 저장하기 위한 엔티티.
 *
 * Redis 정상 → Redis 기반 락 (TTL 30s, 분산 환경)
 * Redis 장애 → 이 테이블 기반 락 (expiresAt 기준 만료)
 *
 * node_id 단위 UNIQUE 제약으로 동시 INSERT 시 하나만 성공 보장.
 */
@Entity
@Table(
        name = "canvas_lock",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_canvas_lock_node",
                columnNames = {"team_id", "graph_id", "node_id"}
        )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CanvasLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "graph_id", nullable = false)
    private Long graphId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void refresh(Duration ttl) {
        this.expiresAt = LocalDateTime.now().plus(ttl);
    }
}
