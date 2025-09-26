package com.example.trader.controller;

import com.example.trader.dto.map.ResponseGraphDto;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    //초기에 해당 페이지의 모든 노드,엣지 조회용
    @GetMapping("/{pageId}")
    public ResponseEntity<ResponseGraphDto> getGraph(@PathVariable Long pageId,@AuthenticationPrincipal UserContext context) {
        ResponseGraphDto graphDto = graphService.getGraph(pageId, context.getUserDto().getId());
        return ResponseEntity.ok(graphDto);
    }

}
