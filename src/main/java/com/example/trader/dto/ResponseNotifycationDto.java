package com.example.trader.dto;

import com.example.trader.entity.NotifycationType;
import com.example.trader.repository.projection.NotifycationView;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;


@Getter
@Builder
@AllArgsConstructor
public class ResponseNotifycationDto {
    private Long id;                   // 읽음 처리/식별
    private String message;            // 사용자에게 보여줄 메시지
    private boolean isRead;            // 스타일링
    private NotifycationType type;     // 아이콘/분기
    private LocalDateTime createdAt;
    private String deeplink;           // 클릭 시 이동 (optional)
    private Long relatedId;            // 연결된 엔티티 id (JoinRequest 등)

    public static ResponseNotifycationDto of(NotifycationView v) {
        return ResponseNotifycationDto.builder()
                .id(v.getId())
                .message(v.getMessage())
                .isRead(Boolean.TRUE.equals(v.getIsRead()))
                .type(NotifycationType.valueOf(v.getType()))
                .createdAt(v.getCreatedAt())
                .deeplink(v.getDeeplink())
                .relatedId(v.getRelatedId())
                .build();
    }
}