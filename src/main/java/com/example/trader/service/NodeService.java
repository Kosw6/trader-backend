package com.example.trader.service;

import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.entity.Node;
import com.example.trader.entity.Note;
import com.example.trader.entity.Page;
import com.example.trader.repository.EdgeRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.repository.NoteRepository;
import com.example.trader.repository.PageRepository;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NodeService {

    private final NodeRepository nodeRepository;
    private final PageRepository pageRepository;
    private final NoteRepository noteRepository;
    private final EdgeRepository edgeRepository;
    private final EntityManager em;

    @Transactional
    public void deleteNode(Long id) {
        edgeRepository.deleteByNodeId(id);
        nodeRepository.deleteById(id);
    }

    //노드의 노트 수정 메서드 + 제목,내용등 수정시에도 동일하게 -> desiredNoteIds가 null이면 노트 업데이트X, [] 빈 배열이면 빈값으로 초기화
    @Transactional
    public ResponseNodeDto updateNode(Long nodeId, RequestNodeDto req) {
        Node node = nodeRepository.findByIdWithLinks(nodeId).orElseThrow(()-> new IllegalArgumentException("연결된 노트링크가 없습니다"));

        // 기본 필드 업데이트
        node.updateBasics(req);


        if (req.isNoteIdsOmitted()) {
            // 변경 없음
        } else if (req.isNoteIdsEmptySet()) {
            // 모두 해제
            syncNotes(node,List.of());
        } else {
            // 주어진 ID들로 완전 동기화
            syncNotes(node,req.getNoteIds());
        }

        return toResponseDto(node);
    }

    @Transactional
    public void updatePosition(Long nodeId,double x, double y) {
        int updated = nodeRepository.updatePosition(nodeId,x, y);
        if (updated == 0) throw new IllegalArgumentException("Node not found or no permission");
    }
    private void syncNotes(Node node, List<Long> desiredNoteIds) {
        Set<Long> current = node.getNoteLinks().stream()
                .map(l -> l.getNote().getId()).collect(Collectors.toSet());
        Set<Long> desired = new HashSet<>(desiredNoteIds);

        //추가할 부분 노트 id셋
        Set<Long> toAdd = new HashSet<>(desired);
        toAdd.removeAll(current);
        //삭제할 부분 노트 id셋
        Set<Long> toRemove = new HashSet<>(current);
        toRemove.removeAll(desired);

        if (!toAdd.isEmpty()) {
            //Note객체를 추가하는데 사실은 id값만 필요하므로 프록시 객체로 만들어서 추가하여 성능상 이점 가저감
            //만약 조회로 찾아와서 넣는다면 어떤 경우 많은 성능하락
            var refs = toAdd.stream()
                    .map(id -> em.getReference(Note.class, id)).toList();
            node.attachAll(refs);
        }
        if (!toRemove.isEmpty()) {
            var refs = toRemove.stream()
                    .map(id -> em.getReference(Note.class, id)).toList();
            node.detachAll(refs);
        }
    }



    @Transactional
    public ResponseNodeDto createNode(RequestNodeDto dto, Long pageId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        Node node = Node.builder()
                .x(dto.getX())
                .y(dto.getY())
                .subject(dto.getSubject())
                .content(dto.getContent())
                .symb(dto.getSymb())
                .page(page)
                .recordDate(dto.getRecordDate())
                // noteId가 있다면 note도 조회해서 set
                .build();
//        // noteIds가 "전송된 경우에만" 링크 동기화
//        dto.getNoteIds().ifPresent(desired -> {
//            // 여기 들어오면 '변경 있음' 의미. 빈 배열([])이면 '전부 해제'.
//            syncNotes(node, desired);
//        });
        if (dto.isNoteIdsOmitted()) {
            // 변경 없음
        } else if (dto.isNoteIdsEmptySet()) {
            // 모두 해제
            syncNotes(node,List.of());
        } else {
            // 주어진 ID들로 완전 동기화
            syncNotes(node,dto.getNoteIds());
        }

        Node saved = nodeRepository.save(node);
        return toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ResponseNodeDto> findAllByPageId(Long pageId) {
        return nodeRepository.findByPageId(pageId).stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResponseNodeDto findById(Long id) {
        return toResponseDto(nodeRepository.findById(id).orElseThrow(()-> new IllegalArgumentException()));
    }

    private ResponseNodeDto toResponseDto(Node node) {
        return ResponseNodeDto.toResponseDto(node);
    }
}
