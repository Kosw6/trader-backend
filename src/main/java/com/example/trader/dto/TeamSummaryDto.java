package com.example.trader.dto;

import com.example.trader.entity.Team;

import java.time.LocalDateTime;

public record TeamSummaryDto(
        Long teamId,
        String teamName,
        Long memberCount,
        String ownerName,
        LocalDateTime createAt
) {

    public static TeamSummaryDto of(Team team, Long memberCount, String ownerName) {
        return new TeamSummaryDto(
                team.getId(),
                team.getTeamName(),
                memberCount,
                ownerName,
                team.getCreatedAt()
        );
    }

}
