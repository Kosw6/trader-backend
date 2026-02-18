package com.example.trader.controller;

import com.example.trader.dto.map.RequestEdgeDto;
import com.example.trader.dto.map.ResponseEdgeDto;
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
    public ResponseEntity<List<ResponseEdgeDto>> getEdgesByPage(@PathVariable Long pageId) {
        return ResponseEntity.ok(edgeService.findAllByPageId(pageId));
    }

    // 엣지 단건 조회
    @GetMapping("/{id}")
    public ResponseEntity<ResponseEdgeDto> getEdge(@PathVariable Long id) {
        return ResponseEntity.ok(edgeService.findById(id));
    }

    // 엣지 생성
    @PostMapping("/page/{pageId}")
    public ResponseEntity<ResponseEdgeDto> createEdge(@RequestBody RequestEdgeDto dto, @PathVariable Long pageId) {
        ResponseEdgeDto saved = edgeService.createEdge(dto, pageId);
        return ResponseEntity.ok(saved);
    }

    // 엣지 수정
    @PutMapping("/{id}/page/{pageId}")
    public ResponseEntity<ResponseEdgeDto> updateEdge(
            @PathVariable Long id,
            @RequestBody RequestEdgeDto dto,
            @PathVariable Long pageId) {
        ResponseEdgeDto updated = edgeService.updateEdge(id, dto, pageId);
        return ResponseEntity.ok(updated);
    }

    // 엣지 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEdge(@PathVariable Long id) {
        edgeService.deleteEdge(id);
        return ResponseEntity.noContent().build();
    }
}
