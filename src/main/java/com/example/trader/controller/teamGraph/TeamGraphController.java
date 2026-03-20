package com.example.trader.controller.teamGraph;

import com.example.trader.common.interceptor.TeamMemberRequired;
import com.example.trader.dto.map.ResponseGraphDto;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@TeamMemberRequired
@RestController
@RequestMapping("/api/teams/{teamId}/graphs")
@RequiredArgsConstructor
public class TeamGraphController {

    private final GraphService graphService;

    @GetMapping("/{graphId}")
    public ResponseEntity<ResponseGraphDto> getTeamGraph(
            @PathVariable Long teamId,
            @PathVariable Long graphId,
            @AuthenticationPrincipal UserContext context
    ) {
        ResponseGraphDto dto = graphService.getTeamGraph(teamId, graphId, context.getUserDto().getId());
        return ResponseEntity.ok(dto);
    }
}
