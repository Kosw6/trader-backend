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

import java.util.List;

@Service
@RequiredArgsConstructor
public class EdgeService {

    private final EdgeRepository edgeRepository;
    private final PageRepository pageRepository;
    private final NodeRepository nodeRepository;

    // ------------------------
    // TEAM METHODS
    // ------------------------

    @Transactional
    public ResponseEdgeDto createTeamEdge(Long teamId, Long graphId, RequestEdgeDto dto) {

        // 1) graph가 팀 소속인지(빠른 검증)
        if (!pageRepository.existsByIdAndDirectoryTeamId(graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        // 2) source/target이 (team, graph)에 속하는지 검증 + 조회
        Node source = nodeRepository.findTeamNode(dto.getSourceId(), graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));
        Node target = nodeRepository.findTeamNode(dto.getTargetId(), graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));

        // 3) 중복 엣지 체크는 "해당 graph 내에서만" (권장)
        if (edgeRepository.existsInPageBySourceTarget(graphId, dto.getSourceId(), dto.getTargetId())) {
            throw new IllegalArgumentException("sourceNode and targetNode edge already exist in this graph");
        }

        // 4) page 엔티티는 굳이 findById로 가져올 필요 없이 proxy로도 OK
        // 하지만 안전하게 팀소속 검증은 이미 했으니 getReference로 성능 챙김 가능
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
        return toResponseDto(saved);
    }

    @Transactional
    public ResponseEdgeDto updateTeamEdge(Long teamId, Long graphId, Long edgeId, RequestEdgeDto dto) {

        // 1) edge가 (team, graph)에 속하는지 검증 + 조회
        Edge toDelete = edgeRepository.findByIdInTeamGraph(edgeId, graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));

        // 2) source/target도 (team, graph)에 속하는지 검증 + 조회
        Node source = nodeRepository.findTeamNode(dto.getSourceId(), graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));
        Node target = nodeRepository.findTeamNode(dto.getTargetId(), graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));

        // 3) (선택) 중복 엣지 체크: 자신(edgeId) 제외하고 같은 source-target 있는지
        if (edgeRepository.existsInPageBySourceTarget(graphId, dto.getSourceId(), dto.getTargetId())
                && !(toDelete.getSource().getId().equals(dto.getSourceId())
                && toDelete.getTarget().getId().equals(dto.getTargetId()))) {
            throw new IllegalArgumentException("sourceNode and targetNode edge already exist in this graph");
        }

        // 4) 너 기존 정책대로 "삭제 후 재생성"
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
        return toResponseDto(saved);
    }

    @Transactional
    public void deleteTeamEdge(Long teamId, Long graphId, Long edgeId) {

        Edge edge = edgeRepository.findByIdInTeamGraph(edgeId, graphId, teamId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));

        edgeRepository.delete(edge);
    }


    @Transactional
    public ResponseEdgeDto createEdge(RequestEdgeDto dto, Long pageId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));

        if(edgeRepository.existsBySourceIdAndTargetId(dto.getSourceId(), dto.getTargetId())){
            throw new IllegalArgumentException("souceNode and targetNode already exist");
        }

        Node source = nodeRepository.findById(dto.getSourceId())
                .orElseThrow(() -> new IllegalArgumentException("sourceNode not found"));
        Node target = nodeRepository.findById(dto.getTargetId())
                .orElseThrow(() -> new IllegalArgumentException("targetNode not found"));

        if(!source.getPage().equals(target.getPage())){
            throw new IllegalArgumentException("sourceNodePage and targetNodePage must be equal");
        }

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
        return toResponseDto(saved);
    }

    @Transactional
    public ResponseEdgeDto updateEdge(Long id, RequestEdgeDto dto, Long pageId) {
        Edge toDelete = edgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Edge not found"));
        if (!toDelete.getPage().getId().equals(pageId)) {
            throw new IllegalArgumentException("Edge does not belong to the page");
        }
        edgeRepository.deleteById(id);
        edgeRepository.flush();
        return createEdge(dto, pageId);
    }

    @Transactional
    public void deleteEdge(Long id) {
        edgeRepository.deleteById(id);
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

