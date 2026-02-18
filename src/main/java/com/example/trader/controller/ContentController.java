package com.example.trader.controller;

import com.example.trader.dto.map.*;
import com.example.trader.security.details.UserContext;
import com.example.trader.security.service.SecurityService;
import com.example.trader.service.ContentCoordinator;
import com.example.trader.service.DirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/content")
public class ContentController {

    private final ContentCoordinator coordinator;
    private final DirectoryService directoryService;

    /** 1) 초기 전체 로딩: 디렉토리[] + 페이지[] */
    @GetMapping("/init")
    public InitPayload init(@AuthenticationPrincipal UserContext user) {
        return coordinator.loadAll(user.getUserDto().getId());
    }

    /** 2) 디렉토리 수정/이동 → 해당 디렉토리 + 페이지 목록 재전달
     * 기존처럼 이동/수정을 따로 API를 제공하지않고 include에 pages가 포함되어있을때(디렉토리 이동)만 재갱신, 포함되어있지 않다면 재갱신X
     * */
    @PatchMapping("/directories/{id}")
    public ResponseEntity<?> updateDirectory(@PathVariable Long id,
                                                 @Validated @RequestBody UpdateDirReq req,
                                                 @AuthenticationPrincipal UserContext user,
                                                 @RequestParam(required = false) String include) {
        directoryService.updateDirectory(id, req, user.getUserDto().getId());
        if ("pages".equals(include)) {
            DirectoryWithPagesDto dto = coordinator.loadDirectoryWithPages(id, user.getUserDto().getId());
            return ResponseEntity.ok(dto);
        }
        return ResponseEntity.noContent().build();
    }

    /** 3) 페이지 이동 → 원본/대상 디렉토리 2개 재전달 */
    @PatchMapping("/pages/{id}/move")
    public ChangeResponse movePage(@PathVariable Long id,
                                   @Validated @RequestBody MovePageReq req,
                                   @AuthenticationPrincipal UserContext user) {
        return coordinator.movePageAndReload(id, req.targetDirectoryId(), user.getUserDto().getId());
    }

    /** 4) 디렉토리 삭제 → 삭제된 디렉토리/페이지 ID만 전달 */
    @DeleteMapping("/directories/{id}")
    public DeleteResponse deleteDirectory(@PathVariable Long id,
                                          @AuthenticationPrincipal UserContext user) {
        return coordinator.deleteDirectoryWithPageIds(id, user.getUserDto().getId());
    }
}
