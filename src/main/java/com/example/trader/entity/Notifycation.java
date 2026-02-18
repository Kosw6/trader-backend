package com.example.trader.entity;

import com.example.trader.entity.base.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


import java.util.Map;

@Table(indexes = {
        @Index(name="idx_notify_user_read_created", columnList="user_id,is_read,created_at"),
        @Index(name="idx_notify_related", columnList="user_id,related_type,related_id")
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Entity
public class Notifycation extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long userId;

    @Builder.Default
    @Column(nullable = false)
    private boolean isRead = false;

    //사용자에게 보여줄 알림 문구
    private String message;

    //상세 알람 타입(합류 요청, 합류 승인, 노드 삭제), 어떤 이벤트가 일어났는지
    @Enumerated(EnumType.STRING)
    private NotifycationType notifycationType;

    //알람 관련 타입(합류,팀,노드,노트), 이 알림이 링크되는 대상
    @Enumerated(EnumType.STRING)
    private NotificationRelatedType relatedType;

    //relatedType과 관련된 엔티티의 id값, ex)합류 요청은 relatedId = joinRequestId,승인 거절은 relatedType=TEAM, relatedId=teamId
    private Long relatedId; // null 허용

    @Column(name = "deeplink", length = 200)
    private String deepLink;

    //클릭 : 수락, 거절(isAllow:true,false)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> payload;


    public void markAsRead() {
        this.isRead = true;
    }

    public static Notifycation teamJoinApproved(
            Long userId,
            Long teamId,
            String teamName
    ) {
        return Notifycation.builder()
                .userId(userId)
                .isRead(false)
                .notifycationType(NotifycationType.TEAM_JOIN_APPROVED)

                // 링크 대상: 팀
                .relatedType(NotificationRelatedType.TEAM)
                .relatedId(teamId)

                .message(teamName + " 팀 합류가 승인되었습니다.")
                .deepLink("/teams/" + teamId)

                // payload는 선택 (여기선 없어도 됨)
                .payload(Map.of("isAllow", true))
                .build();
    }

    public static Notifycation teamJoinRejected(
            Long userId,
            Long teamId,
            String teamName
    ) {
        return Notifycation.builder()
                .userId(userId)
                .isRead(false)
                .notifycationType(NotifycationType.TEAM_JOIN_REJECTED)

                // 링크 대상: 팀(또는 null도 가능하지만 일관성 위해 TEAM 추천)
                .relatedType(NotificationRelatedType.TEAM)
                .relatedId(teamId)

                .message(teamName + " 팀 합류가 거절되었습니다.")
                .deepLink("/teams/join")

                .payload(Map.of("isAllow", false))
                .build();
    }

    /**
     * OWNER에게 가는 "합류 요청" 알림
     * - relatedType=JOIN_REQUEST, relatedId=joinRequestId 로 정확히 연결
     * - deepLink는 "요청 목록" 화면
     * - payload는 액션/표시에 필요한 최소 정보만
     */
    public static Notifycation teamJoinRequested(
            Long ownerId,
            Long teamId,
            Long joinRequestId,
            Long requesterId,
            String teamName,
            String requesterNick
    ) {
        return Notifycation.builder()
                .userId(ownerId)
                .isRead(false)
                .notifycationType(NotifycationType.TEAM_JOIN_REQUESTED)

                // 링크 대상: JoinRequest
                .relatedType(NotificationRelatedType.JOIN_REQUEST)
                .relatedId(joinRequestId)

                .message(requesterNick + "님이 " + teamName + " 참가를 요청했습니다.")
                .deepLink("/teams/" + teamId + "/requests")

                // 프론트에서 버튼 클릭 시 requesterId 등이 필요하면 payload에
                .payload(Map.of(
                        "teamId", teamId,
                        "requesterId", requesterId
                ))
                .build();
    }
}
