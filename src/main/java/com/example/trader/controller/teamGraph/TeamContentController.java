package com.example.trader.controller.teamGraph;

import com.example.trader.common.interceptor.TeamMemberRequired;
import com.example.trader.dto.map.*;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.DirectoryService;
import com.example.trader.service.TeamContentCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@TeamMemberRequired
@RestController
@RequestMapping("/api/teams/{teamId}/content")
@RequiredArgsConstructor
public class TeamContentController {
    private final TeamContentCoordinator coordinator;
    private final DirectoryService teamDirectoryService;

    /** 1) 초기 전체 로딩: 디렉토리[] + 페이지[] */
    @GetMapping("/init")
    public InitPayload init(@PathVariable Long teamId) {
        return coordinator.loadAll(teamId);
    }

    /** 2) 디렉토리 수정/이동 → include=pages면 해당 디렉토리+페이지 재전달 */
    @PatchMapping("/directories/{dirId}")
    public ResponseEntity<?> updateDirectory(
            @PathVariable Long teamId,
            @PathVariable Long dirId,
            @Validated @RequestBody UpdateDirReq req,
            @RequestParam(required = false) String include
    ) {
        teamDirectoryService.updateTeamDirectory(teamId, dirId, req);

        if ("pages".equals(include)) {
            DirectoryWithPagesDto dto = coordinator.loadDirectoryWithPages(teamId, dirId);
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity.noContent().build();
    }

    /** 3) 페이지 이동 → 원본/대상 디렉토리 2개 재전달 */
    @PatchMapping("/pages/{pageId}/move")
    public ChangeResponse movePage(
            @PathVariable Long teamId,
            @PathVariable Long pageId,
            @Validated @RequestBody MovePageReq req
    ) {
        return coordinator.movePageAndReload(teamId, pageId, req.targetDirectoryId());
    }

    /** 4) 디렉토리 삭제 (OWNER 전용) → 삭제된 디렉토리/페이지 ID만 전달 */
    @DeleteMapping("/directories/{dirId}")
    public DeleteResponse deleteDirectory(
            @PathVariable Long teamId,
            @PathVariable Long dirId,
            @AuthenticationPrincipal UserContext user
    ) {
        return coordinator.deleteDirectoryWithPageIds(teamId, dirId, user.getUserDto().getId());
    }
}
