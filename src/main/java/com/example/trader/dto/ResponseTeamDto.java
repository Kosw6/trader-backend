package com.example.trader.dto;


import com.example.trader.entity.Team;
import com.example.trader.entity.User;
import com.example.trader.entity.UserTeam;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseTeamDto {
    private Long id;
    private String teamName;
    private List<ResponseUserTeamDto> members;
    private String code;

    public static ResponseTeamDto ofDto(Team team){
        return ResponseTeamDto.builder().id(team.getId())
                .teamName(team.getTeamName())
                .code(team.getCode())
                .members(team.getUserTeams().stream()
                        .map(ResponseUserTeamDto::from)
                        .toList()).build();
    }
}

