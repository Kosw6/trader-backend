package com.example.trader.service;

import com.example.trader.dto.map.RequestEdgeDto;
import com.example.trader.dto.map.ResponseEdgeDto;
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
    public ResponseEdgeDto createEdge(RequestEdgeDto dto, Long pageId) {
        //이미 소스,타겟에 연결된 엣지가 있는지 확인
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        if(edgeRepository.existsBySourceIdAndTargetId(dto.getSourceId(), dto.getTargetId())){
            throw new IllegalArgumentException("souceNode and targetNode already exist");
        }
        Node source = nodeRepository.findById(dto.getSourceId()).orElseThrow(() -> new IllegalArgumentException("sourceNode not found"));
        Node target = nodeRepository.findById(dto.getTargetId()).orElseThrow(() -> new IllegalArgumentException("sourceNode not found"));
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

    //엣지의 소스,타겟 노드가 변경될 경우 삭제 후 재생성을 할 것인지 아니면 수정을 할 것인지
    @Transactional
    public ResponseEdgeDto updateEdge(Long id, RequestEdgeDto dto, Long pageId) {
        Edge toDelete = edgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Edge not found"));
        if (!toDelete.getPage().getId().equals(pageId)) {
            throw new IllegalArgumentException("Edge does not belong to the page");
        }
        edgeRepository.deleteById(id);
        //삭제 먼저 시켜야 혹시나 플러쉬 시기에 생성쿼리가 엉키는걸 방지(edge의 유니크 제약조건때문에)
        edgeRepository.flush();
        ResponseEdgeDto edge = createEdge(dto, pageId);
        // save() 호출 불필요, 트랜잭션 종료 시점에 자동 반영
        return edge;
    }

    @Transactional
    public void deleteEdge(Long id) {
        edgeRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<ResponseEdgeDto> findAllByPageId(Long pageId) {
        return edgeRepository.findAllByPageId(pageId).stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResponseEdgeDto findById(Long id) {
        return toResponseDto(edgeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Edge not found")));
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
