package com.example.trader.controller.teamGraph;

import com.example.trader.common.interceptor.TeamMemberRequired;
import com.example.trader.dto.UpdateNodePositionReq;
import com.example.trader.dto.canvas.EditStartReq;
import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.exception.NodeConflictException;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.NodeService;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@TeamMemberRequired
@RestController
@RequestMapping("/api/teams/{teamId}/graphs/{graphId}/nodes")
@RequiredArgsConstructor
public class TeamNodesController {

    private final NodeService nodeService;
    private final NodeEditSessionService editSessionService;

    @PatchMapping("/{nodeId}/position")
    public ResponseEntity<Void> updatePosition(
            @PathVariable Long teamId,
            @PathVariable Long graphId,
            @PathVariable Long nodeId,
            @RequestBody UpdateNodePositionReq dto
    ) {
        nodeService.updatePositionInTeam(teamId, graphId, nodeId, dto.x(), dto.y());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> deleteNode(
            @PathVariable Long nodeId,
            @PathVariable Long teamId,
            @PathVariable Long graphId
    ) {
        nodeService.deleteTeamNode(teamId, graphId, nodeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<ResponseNodeDto> createTeamNode(
            @RequestBody RequestNodeDto dto,
            @PathVariable Long graphId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserContext context
    ) {
        ResponseNodeDto saved = nodeService.createTeamNode(
                dto,
                teamId,
                graphId,
                context.getUserDto().getId()
        );
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{nodeId}")
    public ResponseEntity<?> updateTeamNode(
            @PathVariable Long nodeId,
            @PathVariable Long graphId,
            @PathVariable Long teamId,
            @RequestBody RequestNodeDto dto,
            @AuthenticationPrincipal UserContext context
    ) {
        Long userId = context.getUserDto().getId();

        try {
            ResponseNodeDto result = nodeService.updateTeamNode(
                    teamId,
                    graphId,
                    nodeId,
                    userId,
                    dto
            );

            editSessionService.endEditSession(teamId, graphId, nodeId, userId);

            return ResponseEntity.ok(result);

        } catch (NodeConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getResult());
        }
    }

    @PostMapping("/{nodeId}/edit-start")
    public ResponseEntity<Void> startEdit(
            @PathVariable Long teamId,
            @PathVariable Long graphId,
            @PathVariable Long nodeId,
            @RequestBody EditStartReq req,
            @AuthenticationPrincipal UserContext context
    ) {
        Long userId = context.getUserDto().getId();

        editSessionService.startEditSession(
                teamId,
                graphId,
                nodeId,
                userId,
                req.baseVersion() != null ? req.baseVersion() : 0,
                req.fields() != null ? req.fields() : List.of()
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{nodeId}/edit-cancel")
    public ResponseEntity<Void> cancelEdit(
            @PathVariable Long teamId,
            @PathVariable Long graphId,
            @PathVariable Long nodeId,
            @AuthenticationPrincipal UserContext context
    ) {
        Long userId = context.getUserDto().getId();

        editSessionService.cancelEditSession(teamId, graphId, nodeId, userId);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{nodeId}/autosave")
    public ResponseEntity<Void> autosave(
            @PathVariable Long teamId,
            @PathVariable Long graphId,
            @PathVariable Long nodeId,
            @RequestBody RequestNodeDto dto,
            @AuthenticationPrincipal UserContext context
    ) {
        Long userId = context.getUserDto().getId();

        boolean saved = editSessionService.saveDraft(
                teamId,
                graphId,
                nodeId,
                userId,
                dto,
                dto.getDirtyFields()
        );

        if (!saved) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{nodeId}")
    public ResponseEntity<ResponseNodeDto> getNode(
            @PathVariable Long graphId,
            @PathVariable Long nodeId,
            @PathVariable Long teamId
    ) {
        return ResponseEntity.ok(nodeService.findTeamNodeById(graphId, nodeId, teamId));
    }
}