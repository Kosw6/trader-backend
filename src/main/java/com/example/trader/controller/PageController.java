// PageController.java
package com.example.trader.controller;

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

@RestController
@RequestMapping("/api/pages")
@RequiredArgsConstructor
public class PageController {

    private final PageService pageService;

    /** 페이지 생성 (201 + Location) */
    @PostMapping
    public ResponseEntity<ResponsePageDto> createPage(@Valid @RequestBody RequestPageDto dto,
                                                      @AuthenticationPrincipal UserContext user) {
        Long userId = user.getUserDto().getId();
        Long pageId = pageService.createPage(dto, userId).getId();

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(pageId)
                .toUri();

        // 생성 직후 단건 조회 DTO 반환(원하면 간단 메타만 반환도 OK)
        ResponsePageDto body = pageService.getPage(pageId, userId);
        return ResponseEntity.created(location).body(body);
    }

    /** 단일 페이지 조회 */
    @GetMapping("/{id}")
    public ResponseEntity<ResponsePageDto> getPage(@PathVariable Long id,
                                                   @AuthenticationPrincipal UserContext user) {
        return ResponseEntity.ok(pageService.getPage(id, user.getUserDto().getId()));
    }

    /** 유저의 전체 페이지 목록 (필요 시) */
    @GetMapping
    public ResponseEntity<List<ResponsePageDto>> listPages(@AuthenticationPrincipal UserContext user,
                                                           @RequestParam(required = false) Long directoryId) {
        Long userId = user.getUserDto().getId();
        if (directoryId != null) {
            // 디렉토리 하위 페이지만
            return ResponseEntity.ok(pageService.getPagesByDirectory(directoryId, userId));
        }
        // 유저 전체 페이지
        return ResponseEntity.ok(pageService.getAllPagesByUserId(userId));
    }

    /** 페이지 수정 (예: 제목/내용) */
    @PutMapping("/{id}")
    public ResponseEntity<ResponsePageDto> updatePage(@PathVariable Long id,
                                                      @Valid @RequestBody UpdatePageReq dto,
                                                      @AuthenticationPrincipal UserContext user) {
        Long userId = user.getUserDto().getId();
        pageService.updatePage(id, dto, userId);
        return ResponseEntity.ok(pageService.getPage(id, userId));
    }

    /** 페이지 삭제 (204) */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePage(@PathVariable Long id,
                                           @AuthenticationPrincipal UserContext user) {
        pageService.deletePage(id, user.getUserDto().getId());
        return ResponseEntity.noContent().build();
    }
}
