package com.example.trader.entity;

import com.example.trader.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "join_request",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_join_request_team_requester", columnNames = {"team_id", "requester_id"})
        },
        indexes = {
                @Index(name = "idx_join_request_team_status", columnList = "team_id,status"),
                @Index(name = "idx_join_request_requester_status", columnList = "requester_id,status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JoinRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 가입 요청자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    // 대상 팀
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JoinRequestStatus status;

    // (선택) 요청 메시지 같은거 있으면
    // @Column(length = 200)
    // private String message;

    private JoinRequest(User requester, Team team) {
        this.requester = requester;
        this.team = team;
        this.status = JoinRequestStatus.PENDING;
    }

    public static JoinRequest create(User requester, Team team) {
        return new JoinRequest(requester, team);
    }

    public void approve() {
        if (this.status != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }
        this.status = JoinRequestStatus.APPROVED;
    }

    public void reject() {
        if (this.status != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }
        this.status = JoinRequestStatus.REJECTED;
    }

    public boolean isPending() {
        return this.status == JoinRequestStatus.PENDING;
    }
}
