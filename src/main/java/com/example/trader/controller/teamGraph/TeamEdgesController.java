package com.example.trader.controller.teamGraph;

import com.example.trader.common.interceptor.TeamMemberRequired;
import com.example.trader.dto.map.RequestEdgeDto;
import com.example.trader.dto.map.ResponseEdgeDto;
import com.example.trader.service.EdgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@TeamMemberRequired
@RestController
@RequestMapping("/api/teams/{teamId}/graphs/{graphId}/edges")
@RequiredArgsConstructor
public class TeamEdgesController {

    private final EdgeService edgeService;

    @PostMapping
    public ResponseEntity<ResponseEdgeDto> createEdge(
            @PathVariable Long teamId,
            @PathVariable Long graphId,
            @RequestBody RequestEdgeDto dto
    ) {
        return ResponseEntity.ok(
                edgeService.createTeamEdge(teamId, graphId, dto)
        );
    }

    @DeleteMapping("/{edgeId}")
    public ResponseEntity<Void> deleteEdge(
            @PathVariable Long teamId,
            @PathVariable Long graphId,
            @PathVariable Long edgeId
    ) {
        edgeService.deleteTeamEdge(teamId, graphId, edgeId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{edgeId}")
    public ResponseEntity<ResponseEdgeDto> updateEdge(
            @PathVariable Long teamId,
            @PathVariable Long graphId,
            @PathVariable Long edgeId,
            @RequestBody RequestEdgeDto dto
    ) {
        return ResponseEntity.ok(
                edgeService.updateTeamEdge(teamId, graphId, edgeId, dto)
        );
    }
}
