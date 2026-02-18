package com.example.trader.dto;

import com.example.trader.entity.TeamRole;
import com.example.trader.entity.UserTeam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResponseUserTeamDto {
    private Long teamId;
    private Long userId;
    private String userName;
    private LocalDateTime createdAt; // createdAt (BaseTimeEntity)
    private TeamRole role;

    public static ResponseUserTeamDto from(UserTeam ut) {
        return ResponseUserTeamDto.builder()
                .teamId(ut.getTeam().getId())
                .userId(ut.getUser().getId())
                .userName(ut.getUser().getNickName()) // 필드명 맞춰서
                .createdAt(ut.getCreatedAt())
                .role(ut.getRole())
                .build();
    }
}
