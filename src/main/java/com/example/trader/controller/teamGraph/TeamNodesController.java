package com.example.trader.controller.teamGraph;

import com.example.trader.common.interceptor.TeamMemberRequired;
import com.example.trader.dto.UpdateNodePositionReq;
import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.NodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

//@TeamMemberRequired//해당 유저가 해당 팀의 팀원인지
@Slf4j
@RestController
@RequestMapping("/api/teams/{teamId}/graphs/{graphId}/nodes")
@RequiredArgsConstructor
public class TeamNodesController {

    private final NodeService nodeService;

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
    //노드 삭제
    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> deleteNode(@PathVariable Long nodeId,@PathVariable Long teamId,@PathVariable Long graphId) {
        nodeService.deleteTeamNode(nodeId,graphId,teamId);
        return ResponseEntity.noContent().build();
    }
    //노드 생성
//    @PostMapping
//    public ResponseEntity<ResponseNodeDto> createTeamNode(@RequestBody RequestNodeDto dto, @PathVariable Long graphId,@PathVariable Long teamId,@AuthenticationPrincipal UserContext context) {
//        ResponseNodeDto saved = nodeService.createTeamNode(dto,teamId, graphId,context.getUserDto().getId());
//        return ResponseEntity.ok(saved);
//    }
    @PostMapping
    public ResponseEntity<ResponseNodeDto> createTeamNode(@RequestBody RequestNodeDto dto,
                                                          @PathVariable Long teamId,
                                                          @PathVariable Long graphId) {
        log.info("createTeam");
        ResponseNodeDto saved = nodeService.createTeamNode(dto, graphId);
        return ResponseEntity.ok(saved);
    }
    //노드 수정
//    @PatchMapping("{nodeId}")
//    public ResponseEntity<ResponseNodeDto> updateTeamNode(
//            @PathVariable Long nodeId,
//            @PathVariable Long graphId,
//            @PathVariable Long teamId,
//            @RequestBody RequestNodeDto dto,
//            @AuthenticationPrincipal UserContext context
//    ) {
//        ResponseNodeDto responseNodeDto = nodeService.updateTeamNode(teamId,graphId,nodeId,context.getUserDto().getId(),dto);
//        return ResponseEntity.ok(responseNodeDto);
//    }
    //poc용
    @PatchMapping("/{nodeId}")
    public ResponseEntity<ResponseNodeDto> updateTeamNode(
            @PathVariable Long nodeId,
            @PathVariable Long teamId,
            @PathVariable Long graphId,
            @RequestBody RequestNodeDto dto
    ) {
        ResponseNodeDto responseNodeDto = nodeService.updateTeamNode(graphId,nodeId,dto);
        return ResponseEntity.ok(responseNodeDto);
    }

    // 노드 단건 조회, 여러건 조회는 teamGraph에서 노드, 엣지 한번에 긁어오는 매핑 이용
    @GetMapping("/{nodeId}")
    public ResponseEntity<ResponseNodeDto> getNode(@PathVariable Long graphId,@PathVariable Long nodeId,@PathVariable Long teamId) {
        return ResponseEntity.ok(nodeService.findTeamNodeById(graphId,nodeId,teamId));
    }
}
