package com.example.trader.controller.teamGraph;

import com.example.trader.common.interceptor.TeamMemberRequired;
import com.example.trader.dto.UpdateNodePositionReq;
import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.canvas.EditStartReq;
import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.exception.NodeConflictException;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.NodeService;
import com.example.trader.ws.raw.RawPresenceBroadcaster;
import com.example.trader.ws.raw.dto.RawCursorMessage;
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

    private final NodeService            nodeService;
    private final NodeEditSessionService editSessionService;
    private final RawPresenceBroadcaster broadcaster;

    // ── 위치 수정 ─────────────────────────────────────────────────────────────

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

    // ── 노드 삭제 ─────────────────────────────────────────────────────────────

    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> deleteNode(
            @PathVariable Long nodeId,
            @PathVariable Long teamId,
            @PathVariable Long graphId
    ) {
        nodeService.deleteTeamNode(nodeId, graphId, teamId);
        return ResponseEntity.noContent().build();
    }

    // ── 노드 생성 ─────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ResponseNodeDto> createTeamNode(
            @RequestBody RequestNodeDto dto,
            @PathVariable Long graphId,
            @PathVariable Long teamId,
            @AuthenticationPrincipal UserContext context
    ) {
        ResponseNodeDto saved = nodeService.createTeamNode(dto, teamId, graphId, context.getUserDto().getId());
        return ResponseEntity.ok(saved);
    }

    // ── 노드 수정 ─────────────────────────────────────────────────────────────

    /**
     * 노드 수정.
     *
     * <p>충돌 흐름:
     * <ul>
     *   <li>PASS / AUTO_MERGE → 200 OK + 저장된 노드 + EDIT_END 브로드캐스트</li>
     *   <li>CONFLICT → 409 + {@link ConflictResult} (diff 포함), EDIT_END 미전송 (A는 아직 편집 중)</li>
     *   <li>force=true → 충돌 무시 강제 저장 → 200 OK + EDIT_END 브로드캐스트</li>
     * </ul>
     */
    @PatchMapping("/{nodeId}")
    public ResponseEntity<?> updateTeamNode(
            @PathVariable Long nodeId,
            @PathVariable Long graphId,
            @PathVariable Long teamId,
            @RequestBody RequestNodeDto dto,
            @AuthenticationPrincipal UserContext context
    ) {
        Long   userId   = context.getUserDto().getId();
        String nickName = context.getUserDto().getNickName();

        try {
            ResponseNodeDto result = nodeService.updateTeamNode(teamId, graphId, nodeId, userId, dto);

            // 저장 성공 → 편집 완료 알림 브로드캐스트
            broadcaster.publishReliable(
                    roomKey(teamId, graphId),
                    editControlMsg("EDIT_END", teamId, graphId, nodeId, userId, nickName, null, null)
            );
            return ResponseEntity.ok(result);

        } catch (NodeConflictException ex) {
            // 충돌 시 EDIT_END 미전송 — A는 아직 편집 결정 중
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getResult());
        }
    }

    // ── 편집 시작 ─────────────────────────────────────────────────────────────

    /**
     * 노드 편집 시작 신호.
     * 클라이언트가 "편집" 버튼을 누를 때 호출.
     *
     * <ol>
     *   <li>Redis에 편집 세션 저장 (baseVersion, 편집 필드 목록)</li>
     *   <li>룸 전체에 EDIT_START 브로드캐스트 → 다른 유저 화면에 "editing..." 표시</li>
     * </ol>
     */
    @PostMapping("/{nodeId}/edit-start")
    public ResponseEntity<Void> startEdit(
            @PathVariable Long teamId,
            @PathVariable Long graphId,
            @PathVariable Long nodeId,
            @RequestBody EditStartReq req,
            @AuthenticationPrincipal UserContext context
    ) {
        Long   userId   = context.getUserDto().getId();
        String nickName = context.getUserDto().getNickName();

        // Redis 편집 세션 저장
        editSessionService.startEditSession(
                teamId, graphId, nodeId, userId,
                req.baseVersion() != null ? req.baseVersion() : 0,
                req.fields() != null ? req.fields() : List.of()
        );

        // 룸 전체에 EDIT_START 알림
        broadcaster.publishReliable(
                roomKey(teamId, graphId),
                editControlMsg("EDIT_START", teamId, graphId, nodeId, userId, nickName,
                        req.fields(), req.baseVersion())
        );

        return ResponseEntity.ok().build();
    }

    // ── 편집 취소 ─────────────────────────────────────────────────────────────

    /**
     * 노드 편집 취소 신호.
     * 저장 없이 편집 모드를 나갈 때 호출 (ESC, 취소 버튼 등).
     *
     * <ol>
     *   <li>Redis 편집 세션 삭제</li>
     *   <li>룸 전체에 EDIT_END 브로드캐스트 → 다른 유저 화면의 "editing..." 제거</li>
     * </ol>
     */
    @PostMapping("/{nodeId}/edit-cancel")
    public ResponseEntity<Void> cancelEdit(
            @PathVariable Long teamId,
            @PathVariable Long graphId,
            @PathVariable Long nodeId,
            @AuthenticationPrincipal UserContext context
    ) {
        Long   userId   = context.getUserDto().getId();
        String nickName = context.getUserDto().getNickName();

        editSessionService.endEditSession(teamId, graphId, nodeId, userId);

        broadcaster.publishReliable(
                roomKey(teamId, graphId),
                editControlMsg("EDIT_END", teamId, graphId, nodeId, userId, nickName, null, null)
        );

        return ResponseEntity.ok().build();
    }

    // ── 노드 단건 조회 ────────────────────────────────────────────────────────

    @GetMapping("/{nodeId}")
    public ResponseEntity<ResponseNodeDto> getNode(
            @PathVariable Long graphId,
            @PathVariable Long nodeId,
            @PathVariable Long teamId
    ) {
        return ResponseEntity.ok(nodeService.findTeamNodeById(graphId, nodeId, teamId));
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /** 룸 키 (CanvasSessionRegistry.roomKey() 와 동일 포맷) */
    private String roomKey(Long teamId, Long graphId) {
        return teamId + ":" + graphId;
    }

    /** __CONTROL__ 메시지 생성 */
    private RawCursorMessage editControlMsg(String subType,
                                            Long teamId, Long graphId, Long nodeId,
                                            Long userId, String nickName,
                                            List<String> fields, Integer baseVersion) {
        return new RawCursorMessage(
                "__CONTROL__", subType,
                teamId, graphId,
                userId, nickName,
                nodeId,
                0, 0,
                System.currentTimeMillis(),
                fields, baseVersion
        );
    }
}
