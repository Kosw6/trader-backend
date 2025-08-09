package com.example.trader.service;

import com.example.trader.dto.EdgeRequestDto;
import com.example.trader.dto.EdgeResponseDto;
import com.example.trader.entity.Edge;
import com.example.trader.entity.Node;
import com.example.trader.entity.Page;
import com.example.trader.repository.EdgeRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.repository.PageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EdgeService {

    private final EdgeRepository edgeRepository;
    private final PageRepository pageRepository;
    private final NodeRepository nodeRepository;

    @Transactional
    public EdgeResponseDto createEdge(EdgeRequestDto dto, Long pageId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        Node source = nodeRepository.findById(dto.getSourceId()).orElseThrow(() -> new IllegalArgumentException("sourceNode not found"));
        Node target = nodeRepository.findById(dto.getTargetId()).orElseThrow(() -> new IllegalArgumentException("sourceNode not found"));
        Edge edge = Edge.builder()
                .source(source)
                .target(target)
                .type(dto.getType())
                .label(dto.getLabel())
                .sourceHandle(dto.getSourceHandle())
                .targetHandle(dto.getTargetHandle())
                .page(page)
                .build();
        Edge saved = edgeRepository.save(edge);
        return toResponseDto(saved);
    }

    @Transactional
    public EdgeResponseDto updateEdge(Long id, EdgeRequestDto dto, Long pageId) {
        Edge edge = edgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Edge not found"));
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        Node source = nodeRepository.findById(dto.getSourceId()).orElseThrow(() -> new IllegalArgumentException("sourceNode not found"));
        Node target = nodeRepository.findById(dto.getTargetId()).orElseThrow(() -> new IllegalArgumentException("sourceNode not found"));
        edge.updateFromDto(dto, page,source,target);
        // save() 호출 불필요, 트랜잭션 종료 시점에 자동 반영
        return toResponseDto(edge);
    }

    @Transactional
    public void deleteEdge(Long id) {
        edgeRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<EdgeResponseDto> findAllByPageId(Long pageId) {
        return edgeRepository.findByPageId(pageId).stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public EdgeResponseDto findById(Long id) {
        return toResponseDto(edgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Edge not found")));
    }

    private EdgeResponseDto toResponseDto(Edge edge) {
        return EdgeResponseDto.builder()
                .id(edge.getId())
                .sourceId(edge.getSource().getId())
                .targetId(edge.getTarget().getId())
                .type(edge.getType())
                .label(edge.getLabel())
                .sourceHandle(edge.getSourceHandle())
                .targetHandle(edge.getTargetHandle())
                .pageId(edge.getPage() != null ? edge.getPage().getId() : null)
                .build();
    }
}
