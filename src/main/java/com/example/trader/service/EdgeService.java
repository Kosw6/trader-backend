package com.example.trader.service;

import com.example.trader.dto.map.RequestEdgeDto;
import com.example.trader.dto.map.ResponseEdgeDto;
import com.example.trader.entity.Edge;
import com.example.trader.entity.Node;
import com.example.trader.entity.Page;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.EdgeRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.repository.PageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EdgeService {

    private final EdgeRepository edgeRepository;
    private final PageRepository pageRepository;
    private final NodeRepository nodeRepository;
    private final GraphCacheService graphCacheService;

    // ------------------------
    // TEAM METHODS
    // ------------------------

    @Transactional
    public ResponseEdgeDto createTeamEdge(Long teamId, Long graphId, RequestEdgeDto dto) {
        if (!pageRepository.existsByIdAndDirectoryTeamId(graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        Node source = nodeRepository.findTeamNode(dto.getSourceId(), graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));
        Node target = nodeRepository.findTeamNode(dto.getTargetId(), graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));

        if (edgeRepository.existsInPageBySourceTarget(graphId, dto.getSourceId(), dto.getTargetId())) {
            throw new IllegalArgumentException("sourceNode and targetNode edge already exist in this graph");
        }

        Page page = pageRepository.getReferenceById(graphId);

        Edge edge = Edge.builder()
                .source(source)
                .target(target)
                .type(dto.getType())
                .label(dto.getLabel())
                .sourceHandle(dto.getSourceHandle())
                .targetHandle(dto.getTargetHandle())
                .page(page)
                .animated(dto.isAnimated())
                .stroke(dto.getStroke())
                .strokeWidth(dto.getStrokeWidth())
                .variant(dto.getVariant())
                .build();

        Edge saved = edgeRepository.save(edge);
        graphCacheService.evictGraph(graphId);
        return toResponseDto(saved);
    }

    @Transactional
    public ResponseEdgeDto updateTeamEdge(Long teamId, Long graphId, Long edgeId, RequestEdgeDto dto) {
        Edge toDelete = edgeRepository.findByIdInTeamGraph(edgeId, graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));

        Node source = nodeRepository.findTeamNode(dto.getSourceId(), graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));
        Node target = nodeRepository.findTeamNode(dto.getTargetId(), graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));

        if (edgeRepository.existsInPageBySourceTarget(graphId, dto.getSourceId(), dto.getTargetId())
                && !(toDelete.getSource().getId().equals(dto.getSourceId())
                && toDelete.getTarget().getId().equals(dto.getTargetId()))) {
            throw new IllegalArgumentException("sourceNode and targetNode edge already exist in this graph");
        }

        edgeRepository.delete(toDelete);
        edgeRepository.flush();

        Page page = pageRepository.getReferenceById(graphId);

        Edge edge = Edge.builder()
                .source(source)
                .target(target)
                .type(dto.getType())
                .label(dto.getLabel())
                .sourceHandle(dto.getSourceHandle())
                .targetHandle(dto.getTargetHandle())
                .page(page)
                .animated(dto.isAnimated())
                .stroke(dto.getStroke())
                .strokeWidth(dto.getStrokeWidth())
                .variant(dto.getVariant())
                .build();

        Edge saved = edgeRepository.save(edge);
        graphCacheService.evictGraph(graphId);
        return toResponseDto(saved);
    }

    @Transactional
    public void deleteTeamEdge(Long teamId, Long graphId, Long edgeId) {
        Edge edge = edgeRepository.findByIdInTeamGraph(edgeId, graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));

        edgeRepository.delete(edge);
        graphCacheService.evictGraph(graphId);
    }

    // ------------------------
    // PERSONAL METHODS
    // ------------------------

    @Transactional
    public ResponseEdgeDto createEdge(RequestEdgeDto dto, Long pageId, Long userId) {
        // 1) page 소유권 체크
        if (!pageRepository.existsByIdAndUserId(pageId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        // 2) source / target 조회
        Node source = nodeRepository.findById(dto.getSourceId())
                .orElseThrow(() -> new IllegalArgumentException("sourceNode not found"));
        Node target = nodeRepository.findById(dto.getTargetId())
                .orElseThrow(() -> new IllegalArgumentException("targetNode not found"));

        // 3) source / target 이 둘 다 해당 page에 속하는지 검증
        if (!source.getPage().getId().equals(pageId) || !target.getPage().getId().equals(pageId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        // 4) 같은 page 안에서만 중복 edge 체크
        if (edgeRepository.existsInPageBySourceTarget(pageId, dto.getSourceId(), dto.getTargetId())) {
            throw new IllegalArgumentException("sourceNode and targetNode already exist in this page");
        }

        Page page = pageRepository.getReferenceById(pageId);

        Edge edge = Edge.builder()
                .source(source)
                .target(target)
                .type(dto.getType())
                .label(dto.getLabel())
                .sourceHandle(dto.getSourceHandle())
                .targetHandle(dto.getTargetHandle())
                .page(page)
                .animated(dto.isAnimated())
                .stroke(dto.getStroke())
                .strokeWidth(dto.getStrokeWidth())
                .variant(dto.getVariant())
                .build();

        Edge saved = edgeRepository.save(edge);
        graphCacheService.evictGraph(pageId);
        return toResponseDto(saved);
    }

    @Transactional
    public ResponseEdgeDto updateEdge(Long edgeId, RequestEdgeDto dto, Long pageId, Long userId) {
        // 1) page 소유권 체크
        if (!pageRepository.existsByIdAndUserId(pageId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        // 2) 기존 edge가 해당 page에 속하는지 확인
        Edge toDelete = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new IllegalArgumentException("Edge not found"));

        if (!toDelete.getPage().getId().equals(pageId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        // 3) 새 source / target 검증
        Node source = nodeRepository.findById(dto.getSourceId())
                .orElseThrow(() -> new IllegalArgumentException("sourceNode not found"));
        Node target = nodeRepository.findById(dto.getTargetId())
                .orElseThrow(() -> new IllegalArgumentException("targetNode not found"));

        if (!source.getPage().getId().equals(pageId) || !target.getPage().getId().equals(pageId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        // 4) 자기 자신 제외 중복 체크
        if (edgeRepository.existsInPageBySourceTarget(pageId, dto.getSourceId(), dto.getTargetId())
                && !(toDelete.getSource().getId().equals(dto.getSourceId())
                && toDelete.getTarget().getId().equals(dto.getTargetId()))) {
            throw new IllegalArgumentException("sourceNode and targetNode already exist in this page");
        }

        edgeRepository.delete(toDelete);
        edgeRepository.flush();

        Page page = pageRepository.getReferenceById(pageId);

        Edge edge = Edge.builder()
                .source(source)
                .target(target)
                .type(dto.getType())
                .label(dto.getLabel())
                .sourceHandle(dto.getSourceHandle())
                .targetHandle(dto.getTargetHandle())
                .page(page)
                .animated(dto.isAnimated())
                .stroke(dto.getStroke())
                .strokeWidth(dto.getStrokeWidth())
                .variant(dto.getVariant())
                .build();

        Edge saved = edgeRepository.save(edge);
        graphCacheService.evictGraph(pageId);
        return toResponseDto(saved);
    }

    @Transactional
    public void deleteEdge(Long pageId, Long edgeId, Long userId) {
        // 1) page 소유권 체크
        if (!pageRepository.existsByIdAndUserId(pageId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        // 2) edge가 해당 page에 속하는지 확인
        Edge edge = edgeRepository.findById(edgeId)
                .orElseThrow(() -> new IllegalArgumentException("Edge not found"));

        if (!edge.getPage().getId().equals(pageId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        edgeRepository.delete(edge);
        graphCacheService.evictGraph(pageId);
    }

    private ResponseEdgeDto toResponseDto(Edge edge) {
        return ResponseEdgeDto.builder()
                .id(edge.getId())
                .sourceId(edge.getSource().getId())
                .targetId(edge.getTarget().getId())
                .type(edge.getType())
                .label(edge.getLabel())
                .sourceHandle(edge.getSourceHandle())
                .targetHandle(edge.getTargetHandle())
                .pageId(edge.getPage() != null ? edge.getPage().getId() : null)
                .animated(edge.isAnimated())
                .stroke(edge.getStroke())
                .strokeWidth(edge.getStrokeWidth())
                .variant(edge.getVariant())
                .build();
    }
}