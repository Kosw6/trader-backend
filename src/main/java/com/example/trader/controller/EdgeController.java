package com.example.trader.controller;

import com.example.trader.dto.EdgeRequestDto;
import com.example.trader.dto.EdgeResponseDto;
import com.example.trader.service.EdgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/edges")
@RequiredArgsConstructor
public class EdgeController {

    private final EdgeService edgeService;

    // 특정 페이지의 엣지 전체 조회
    @GetMapping("/page/{pageId}")
    public ResponseEntity<List<EdgeResponseDto>> getEdgesByPage(@PathVariable Long pageId) {
        return ResponseEntity.ok(edgeService.findAllByPageId(pageId));
    }

    // 엣지 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<EdgeResponseDto> getEdge(@PathVariable Long id) {
        return ResponseEntity.ok(edgeService.findById(id));
    }

    // 엣지 생성
    @PostMapping("/page/{pageId}")
    public ResponseEntity<EdgeResponseDto> createEdge(@RequestBody EdgeRequestDto dto, @PathVariable Long pageId) {
        EdgeResponseDto saved = edgeService.createEdge(dto, pageId);
        return ResponseEntity.ok(saved);
    }

    // 엣지 수정
    @PutMapping("/{id}/page/{pageId}")
    public ResponseEntity<EdgeResponseDto> updateEdge(
            @PathVariable Long id,
            @RequestBody EdgeRequestDto dto,
            @PathVariable Long pageId) {
        EdgeResponseDto updated = edgeService.updateEdge(id, dto, pageId);
        return ResponseEntity.ok(updated);
    }

    // 엣지 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEdge(@PathVariable Long id) {
        edgeService.deleteEdge(id);
        return ResponseEntity.noContent().build();
    }
}
