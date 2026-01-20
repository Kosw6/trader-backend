package com.example.trader.service;

import com.example.trader.dto.map.ResponseEdgeDto;
import com.example.trader.dto.map.ResponseGraphDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.entity.Edge;
import com.example.trader.entity.Node;
import com.example.trader.repository.EdgeRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.repository.PageRepository;
import com.example.trader.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GraphService {

    private final PageRepository pageRepository;
    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final SecurityService securityService;
    @Transactional(readOnly = true)
    public ResponseGraphDto getGraph(Long pageId,Long userId) {

        // 소유권/존재 검증(가벼운 쿼리)
        if (!pageRepository.existsByIdAndUserId(pageId, userId)) {
            throw new IllegalArgumentException("Page not found or no permission");
        }
        // 두 번의 간단한 쿼리로 안전하게 조회
        List<ResponseNodeDto> nodeDtos = nodeRepository.findAllFetchByPageId(pageId).stream().map(this::toResponseNodeDto)
                .collect(Collectors.toList());
        List<ResponseEdgeDto> edgeDtos = edgeRepository.findAllByPageId(pageId).stream().map(this::toResponseEdgeDto)
                .collect(Collectors.toList());
        return new ResponseGraphDto(pageId,nodeDtos,edgeDtos);
    }
    private ResponseNodeDto toResponseNodeDto(Node node) {
        return ResponseNodeDto.toResponseDto(node);
    }
    private ResponseEdgeDto toResponseEdgeDto(Edge edge) {
        return ResponseEdgeDto.builder()
                .id(edge.getId())
                .sourceId(edge.getSource().getId())
                .targetId(edge.getTarget().getId())
                .type(edge.getType())
                .label(edge.getLabel())
                .sourceHandle(edge.getSourceHandle())
                .targetHandle(edge.getTargetHandle())
                .pageId(edge.getPage() != null ? edge.getPage().getId() : null)
                .variant(edge.getVariant())
                .stroke(edge.getStroke())
                .strokeWidth(edge.getStrokeWidth())
                .animated(edge.isAnimated())
                .build();
    }
}
