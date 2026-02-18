package com.example.trader.dto;

import com.example.trader.entity.TeamRole;

public record UpdateUserTeamRoleDto(
        Long userId,
        Long teamId,
        TeamRole role
) {}
