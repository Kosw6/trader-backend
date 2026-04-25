package com.example.trader.controller.teamGraph;

import com.example.trader.common.interceptor.TeamMemberRequired;
import com.example.trader.dto.map.RequestDirectoryDto;
import com.example.trader.dto.map.ResponseDirectoryDto;
import com.example.trader.dto.map.UpdateDirReq;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.DirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@TeamMemberRequired
@RestController
@RequestMapping("/api/teams/{teamId}/directories")
@RequiredArgsConstructor
public class TeamDirectoriesController {

    private final DirectoryService directoryService;

    /** 팀 디렉토리 생성 */
    @PostMapping
    public ResponseEntity<ResponseDirectoryDto> createDirectory(
            @PathVariable Long teamId,
            @RequestBody RequestDirectoryDto dto
    ) {
        ResponseDirectoryDto saved = directoryService.createTeamDirectory(teamId, dto);
        return ResponseEntity.ok(saved);
    }

    /** 팀 디렉토리 단건 조회 */
    @GetMapping("/{dirId}")
    public ResponseEntity<ResponseDirectoryDto> getDirectory(
            @PathVariable Long teamId,
            @PathVariable Long dirId
    ) {
        return ResponseEntity.ok(directoryService.getTeamDirectory(teamId, dirId));
    }

    /** 팀 디렉토리 목록 */
    @GetMapping
    public List<ResponseDirectoryDto> list(
            @PathVariable Long teamId
    ) {
        return directoryService.getAllTeamDirectories(teamId);
    }

    /** 팀 디렉토리 이름 수정 */
    @PutMapping("/{dirId}")
    public ResponseEntity<ResponseDirectoryDto> updateDirectory(
            @PathVariable Long teamId,
            @PathVariable Long dirId,
            @RequestBody UpdateDirReq dto
    ) {
        return ResponseEntity.ok(directoryService.updateTeamDirectory(teamId, dirId, dto));
    }

    /** 팀 디렉토리 삭제 (OWNER 전용) */
    @DeleteMapping("/{dirId}")
    public ResponseEntity<Void> deleteDirectory(
            @PathVariable Long teamId,
            @PathVariable Long dirId,
            @AuthenticationPrincipal UserContext user
    ) {
        directoryService.deleteTeamDirectory(teamId, dirId, user.getUserDto().getId());
        return ResponseEntity.noContent().build();
    }

}
