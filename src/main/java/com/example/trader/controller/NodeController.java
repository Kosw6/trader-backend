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
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;

    // 특정 페이지의 노드 전체 조회
    @GetMapping("/page/{pageId}")
    public ResponseEntity<List<ResponseNodeDto>> getNodesByPage(@PathVariable Long pageId,@AuthenticationPrincipal UserContext user) {

        return ResponseEntity.ok(nodeService.findAllByPageId(pageId,user.getUserDto().getId()));
    }

    // 노드 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<ResponseNodeDto> getNode(@PathVariable Long id,@AuthenticationPrincipal UserContext user) {
        return ResponseEntity.ok(nodeService.findPersonalNodeById(id,user.getUserDto().getId()));
    }

    // 노드 생성
    @PostMapping("/page/{pageId}")
    public ResponseEntity<ResponseNodeDto> createNode(@RequestBody RequestNodeDto dto, @PathVariable Long pageId) {
        ResponseNodeDto saved = nodeService.createNode(dto, pageId);
        return ResponseEntity.ok(saved);
    }

    // 노드 위치 수정(개인용)
    @PatchMapping("{id}/position")
    public ResponseEntity<Void> updateNodePosition(
            @PathVariable Long id,
            @RequestBody UpdateNodePositionReq dto,
            @AuthenticationPrincipal UserContext user
    ) {
        nodeService.updatePosition(id,user.getUserDto().getId(), dto.x(),dto.y());
        return ResponseEntity.noContent().build();
    }


    //노드 수정
    @PatchMapping("{nodeId}")
    public ResponseEntity<ResponseNodeDto> updateNode(
            @PathVariable Long nodeId,
            @RequestBody RequestNodeDto dto,
            @AuthenticationPrincipal UserContext user
    ) {
        ResponseNodeDto responseNodeDto = nodeService.updateNode(nodeId, dto,user.getUserDto().getId());
        return ResponseEntity.ok(responseNodeDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNode(@PathVariable Long id,@AuthenticationPrincipal UserContext user) {
        nodeService.deleteNode(id,user.getUserDto().getId());
        return ResponseEntity.noContent().build();
    }
}
