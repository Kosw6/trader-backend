package com.example.trader.controller.teamGraph;

import com.example.trader.common.interceptor.TeamMemberRequired;
import com.example.trader.dto.map.RequestPageDto;
import com.example.trader.dto.map.UpdatePageReq;
import com.example.trader.dto.map.ResponsePageDto;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.PageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@TeamMemberRequired
@RestController
@RequestMapping("/api/teams/{teamId}/pages")
@RequiredArgsConstructor
public class TeamPagesController {

    private final PageService pageService;

    /** 팀 페이지 목록 조회 (directoryId 파라미터로 필터 가능) */
    @GetMapping
    public ResponseEntity<List<ResponsePageDto>> getPages(@PathVariable Long teamId,
                                                          @RequestParam(required = false) Long directoryId) {
        if (directoryId != null) {
            return ResponseEntity.ok(pageService.getTeamPagesByDirectory(directoryId, teamId));
        }
        return ResponseEntity.ok(pageService.getAllTeamPages(teamId));
    }

    /** 팀 페이지 생성 (201 + Location) */
    @PostMapping
    public ResponseEntity<ResponsePageDto> createPage(@PathVariable Long teamId,
                                                      @Valid @RequestBody RequestPageDto dto,
                                                      @AuthenticationPrincipal UserContext user) {
        Long userId = user.getUserDto().getId();

        Long pageId = pageService.createTeamPage(teamId, dto, userId).getId();

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(pageId)
                .toUri();

        ResponsePageDto body = pageService.getTeamPage(teamId, pageId);
        return ResponseEntity.created(location).body(body);
    }

    /** 팀 페이지 단건 조회 */
    @GetMapping("/{pageId}")
    public ResponseEntity<ResponsePageDto> getPage(@PathVariable Long teamId,
                                                   @PathVariable Long pageId) {
        return ResponseEntity.ok(pageService.getTeamPage(teamId, pageId));
    }

    /** 팀 페이지 수정 */
    @PutMapping("/{pageId}")
    public ResponseEntity<ResponsePageDto> updatePage(@PathVariable Long teamId,
                                                      @PathVariable Long pageId,
                                                      @Valid @RequestBody UpdatePageReq dto) {
        pageService.updateTeamPage(teamId, pageId, dto);
        return ResponseEntity.ok(pageService.getTeamPage(teamId, pageId));
    }

    /** 팀 페이지 삭제 (단건) */
    @DeleteMapping("/{pageId}")
    public ResponseEntity<Void> deletePage(@PathVariable Long teamId,
                                           @PathVariable Long pageId) {
        pageService.deleteTeamPage(teamId, pageId);
        return ResponseEntity.noContent().build();
    }
}
