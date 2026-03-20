package com.example.trader.controller;

import com.example.trader.dto.UpdateNodePositionReq;
import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pages/{pageId}/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;

    @GetMapping
    public ResponseEntity<List<ResponseNodeDto>> getNodesByPage(
            @PathVariable Long pageId,
            @AuthenticationPrincipal UserContext user
    ) {
        return ResponseEntity.ok(nodeService.findAllByPageId(pageId, user.getUserDto().getId()));
    }

    @PostMapping
    public ResponseEntity<ResponseNodeDto> createNode(
            @PathVariable Long pageId,
            @RequestBody RequestNodeDto dto,
            @AuthenticationPrincipal UserContext user
    ) {
        ResponseNodeDto saved = nodeService.createNode(dto, pageId, user.getUserDto().getId());
        return ResponseEntity.ok(saved);
    }

    @PatchMapping("/{nodeId}")
    public ResponseEntity<ResponseNodeDto> updateNode(
            @PathVariable Long pageId,
            @PathVariable Long nodeId,
            @RequestBody RequestNodeDto dto,
            @AuthenticationPrincipal UserContext user
    ) {
        ResponseNodeDto response = nodeService.updateNode(pageId, nodeId, dto, user.getUserDto().getId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{nodeId}/position")
    public ResponseEntity<Void> updateNodePosition(
            @PathVariable Long pageId,
            @PathVariable Long nodeId,
            @RequestBody UpdateNodePositionReq dto,
            @AuthenticationPrincipal UserContext user
    ) {
        nodeService.updatePosition(pageId, nodeId, user.getUserDto().getId(), dto.x(), dto.y());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{nodeId}")
    public ResponseEntity<ResponseNodeDto> getNode(
            @PathVariable Long pageId,
            @PathVariable Long nodeId,
            @AuthenticationPrincipal UserContext user
    ) {
        return ResponseEntity.ok(nodeService.findPersonalNodeById(pageId, nodeId, user.getUserDto().getId()));
    }

    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> deleteNode(
            @PathVariable Long pageId,
            @PathVariable Long nodeId,
            @AuthenticationPrincipal UserContext user
    ) {
        nodeService.deleteNode(pageId, nodeId, user.getUserDto().getId());
        return ResponseEntity.noContent().build();
    }
}
