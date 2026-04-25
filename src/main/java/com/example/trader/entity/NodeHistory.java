package com.example.trader.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 팀 노드의 버전별 변경 이력.
 * 충돌 감지 시 Redis 힌트가 불완전할 경우 DB fallback으로 사용.
 *
 * 저장 시점: NodeService.updateTeamNode() 성공 후
 */
@Entity
@Table(
        name = "node_history",
        indexes = {
                @Index(name = "idx_nh_node_version", columnList = "node_id, version")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "graph_id", nullable = false)
    private Long graphId;

    /** 이 변경으로 만들어진 버전 번호 */
    @Column(nullable = false)
    private Integer version;

    /** 변경한 userId */
    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    /**
     * 변경된 필드명 목록을 JSON 배열로 저장.
     * ex) ["subject","content"]
     */
    @Column(name = "changed_fields", columnDefinition = "text", nullable = false)
    private String changedFields;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
}
