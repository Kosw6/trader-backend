package com.example.trader.controller;

import com.example.trader.dto.NodeRequestDto;
import com.example.trader.dto.NodeResponseDto;
import com.example.trader.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
public class NodeController {

    private final NodeService nodeService;

    // 특정 페이지의 노드 전체 조회
    @GetMapping("/page/{pageId}")
    public ResponseEntity<List<NodeResponseDto>> getNodesByPage(@PathVariable Long pageId) {
        return ResponseEntity.ok(nodeService.findAllByPageId(pageId));
    }

    // 노드 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<NodeResponseDto> getNode(@PathVariable Long id) {
        return ResponseEntity.ok(nodeService.findById(id));
    }

    // 노드 생성
    @PostMapping("/page/{pageId}")
    public ResponseEntity<NodeResponseDto> createNode(@RequestBody NodeRequestDto dto, @PathVariable Long pageId) {
        NodeResponseDto saved = nodeService.createNode(dto, pageId);
        return ResponseEntity.ok(saved);
    }

    // 노드 수정
    @PutMapping("/{id}/page/{pageId}")
    public ResponseEntity<NodeResponseDto> updateNode(
            @PathVariable Long id,
            @RequestBody NodeRequestDto dto,
            @PathVariable Long pageId
    ) {
        NodeResponseDto updated = nodeService.updateNode(id, dto, pageId);
        return ResponseEntity.ok(updated);
    }

    // 노드 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNode(@PathVariable Long id) {
        nodeService.deleteNode(id);
        return ResponseEntity.noContent().build();
    }
}
