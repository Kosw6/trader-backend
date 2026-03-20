package com.example.trader.controller;

import com.example.trader.dto.map.RequestEdgeDto;
import com.example.trader.dto.map.ResponseEdgeDto;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.EdgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pages/{pageId}/edges")
@RequiredArgsConstructor
public class EdgeController {

    private final EdgeService edgeService;

    @PostMapping
    public ResponseEntity<ResponseEdgeDto> createEdge(
            @PathVariable Long pageId,
            @RequestBody RequestEdgeDto dto,
            @AuthenticationPrincipal UserContext user
    ) {
        ResponseEdgeDto saved = edgeService.createEdge(dto, pageId, user.getUserDto().getId());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{edgeId}")
    public ResponseEntity<ResponseEdgeDto> updateEdge(
            @PathVariable Long pageId,
            @PathVariable Long edgeId,
            @RequestBody RequestEdgeDto dto,
            @AuthenticationPrincipal UserContext user
    ) {
        ResponseEdgeDto updated = edgeService.updateEdge(edgeId, dto, pageId, user.getUserDto().getId());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{edgeId}")
    public ResponseEntity<Void> deleteEdge(
            @PathVariable Long pageId,
            @PathVariable Long edgeId,
            @AuthenticationPrincipal UserContext user
    ) {
        edgeService.deleteEdge(pageId, edgeId, user.getUserDto().getId());
        return ResponseEntity.noContent().build();
    }
}
