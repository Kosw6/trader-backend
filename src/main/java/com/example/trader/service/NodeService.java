package com.example.trader.service;

import com.example.trader.common.util.BatchUtils;
import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.entity.Node;
import com.example.trader.entity.NodeNoteLink;
import com.example.trader.entity.Note;
import com.example.trader.entity.Page;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.*;
import com.example.trader.repository.projection.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeService {

    private final NodeRepository nodeRepository;
    private final PageRepository pageRepository;
    private final NoteRepository noteRepository;
    private final EdgeRepository edgeRepository;
    private final UserTeamRepository userTeamRepository;
    private final NodeNoteLinkRepository nodeNoteLinkRepository;
    private final EntityManager em;
    private final ObjectMapper objectMapper;

    @Transactional
    public void deleteNode(Long id, Long userId) {
        if (!nodeRepository.existsByIdAndPageUserId(id, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }
        edgeRepository.deleteByNodeId(id);
        nodeRepository.deleteById(id);
    }

    @Transactional
    public void deleteTeamNode(Long teamId, Long graphId, Long nodeId) {

        // 1) node가 (team, graph)에 속하는지 검증 (없으면 권한/존재 X)
        if (!nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(nodeId, graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        // 2) 연결된 edge 먼저 삭제 (FK/제약 때문에)
        edgeRepository.deleteByNodeId(nodeId);

        // 3) node 삭제
        nodeRepository.deleteById(nodeId);
    }

    //노드의 노트 수정 메서드 + 제목,내용등 수정시에도 동일하게 -> desiredNoteIds가 null이면 노트 업데이트X, [] 빈 배열이면 빈값으로 초기화
    @Transactional
    public ResponseNodeDto updateNode(Long nodeId, RequestNodeDto req, Long userId) {
        if (!nodeRepository.existsByIdAndPageUserId(nodeId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }
        Node node = nodeRepository.findByIdWithLinks(nodeId).orElseThrow(() -> new IllegalArgumentException("연결된 노트링크가 없습니다"));

        // 기본 필드 업데이트
        node.updateBasics(req);


        if (req.isNoteIdsOmitted()) {
            // 변경 없음
        } else if (req.isNoteIdsEmptySet()) {
            // 모두 해제
            syncNotes(node, List.of());
        } else {
            // 주어진 ID들로 완전 동기화
            syncNotes(node, req.getNoteIds());
        }

        return ResponseNodeDto.toResponseDto(node);
    }

    @Transactional
    public ResponseNodeDto updateTeamNode(Long teamId, Long nodeId, Long userId, RequestNodeDto req) {
        // 팀 권한 체크 (node belongs to team etc)
        if (!nodeRepository.existsByIdAndPageDirectoryTeamId(nodeId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        Node node = nodeRepository.findByIdWithLinks(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("연결된 노트링크가 없습니다"));

        node.updateBasics(req);

        if (req.isNoteIdsOmitted()) {
            // 변경 없음
        } else if (req.isNoteIdsEmptySet()) {
            // ✅ “내 링크만” 모두 해제 (남의 링크는 그대로)
            syncTeamNotesMineOnly(node, List.of(), userId);
        } else {
            // ✅ “내 링크만” 주어진 ID로 동기화
            syncTeamNotesMineOnly(node, req.getNoteIds(), userId);
        }

        return ResponseNodeDto.toResponseDto(node);
    }

    @Transactional
    public void updatePosition(Long nodeId, Long userId, double x, double y) {
        if (!nodeRepository.existsByIdAndPageUserId(nodeId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }
        int updated = nodeRepository.updatePosition(nodeId, x, y);
        if (updated == 0) throw new IllegalArgumentException("Node not found or no permission");
    }

    @Transactional
    public void updatePositionInTeam(Long teamId, Long graphId, Long nodeId, double x, double y) {
        if (nodeRepository.updatePositionInTeam(teamId, graphId, nodeId, x, y) == 0) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }
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

    //TODO
    /**개인의 노트만 노드에 연결을 시킬 수 있으며 타인의 연결은 끊을 수 없다
     * 프론트 페이지에서 노드 수정시에 맨 위에 자신의 연결부터 뜨고 x버튼은 자신의 노트연결만 표시하게 수정
    */
    private void syncTeamNotesMineOnly(Node node, List<Long> desiredNoteIds, Long userId) {
        Set<Long> desired = new HashSet<>(desiredNoteIds == null ? List.of() : desiredNoteIds);

        // 1) 현재 링크 중 "내 노트"만 골라 현재 상태 만들기
        Set<Long> currentMine = node.getNoteLinks().stream()
                .filter(link -> link.getNote().getUser().getId().equals(userId)) // ✅ 내 노트만
                .map(link -> link.getNote().getId())
                .collect(Collectors.toSet());

        // 2) 추가/삭제는 내 노트 링크에 대해서만 계산
        Set<Long> toAdd = new HashSet<>(desired);
        toAdd.removeAll(currentMine);

        Set<Long> toRemove = new HashSet<>(currentMine);
        toRemove.removeAll(desired);

        // 3) ✅ 보안: toAdd에 들어온 noteIds가 전부 "내 노트"가 맞는지 DB로 검증
        // (프론트에서 막아도 서버는 반드시 재검증)
        if (!toAdd.isEmpty()) {
            List<Long> allowedIds = noteRepository.findIdsByUserIdAndIdIn(userId, toAdd);
            if (allowedIds.size() != toAdd.size()) {
                throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE); // 또는 INVALID_NOTE_ACCESS
            }
            var refs = allowedIds.stream().map(id -> em.getReference(Note.class, id)).toList();
            node.attachAll(refs);
        }

        // 4) 삭제는 "내 노트 링크"만 제거하므로 DB검증 없이도 안전
        if (!toRemove.isEmpty()) {
            var refs = toRemove.stream().map(id -> em.getReference(Note.class, id)).toList();
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
            syncNotes(node, List.of());
        } else {
            // 주어진 ID들로 완전 동기화
            syncNotes(node, dto.getNoteIds());
        }

        Node saved = nodeRepository.save(node);
        return ResponseNodeDto.toResponseDto(saved);
    }

    @Transactional
    public ResponseNodeDto createTeamNode(RequestNodeDto dto, Long teamId, Long pageId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException("Page not found"));
        //해당 페이지가 팀에 속해있는지
        if (!pageRepository.existsByIdAndDirectoryTeamId(pageId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }
        Node node = Node.builder()
                .x(dto.getX())
                .y(dto.getY())
                .subject(dto.getSubject())
                .content(dto.getContent())
                .symb(dto.getSymb())
                .page(page)
                .recordDate(dto.getRecordDate())
                .build();
        if (dto.isNoteIdsOmitted()) {
            // 변경 없음
        } else if (dto.isNoteIdsEmptySet()) {
            // 모두 해제
            syncNotes(node, List.of());
        } else {
            // 주어진 ID들로 완전 동기화
            syncNotes(node, dto.getNoteIds());
        }

        Node saved = nodeRepository.save(node);
        return ResponseNodeDto.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ResponseNodeDto> findAllByPageId(Long pageId, Long userId) {
        if (!pageRepository.existsByIdAndUserId(pageId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }
        // fetch기반 쿼리실행
        return nodeRepository.findAllFetchByPageId(pageId).stream().map(ResponseNodeDto::toResponseDtoToPreviewList).collect(Collectors.toList());


    }

    @Transactional(readOnly = true)
    public ResponseNodeDto findPersonalNodeById(Long id, Long userId) {
        Node node = nodeRepository.findPersonalNodeWithLinks(id, userId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));
        return ResponseNodeDto.toResponseDto(node);
    }

    @Transactional(readOnly = true)
    public ResponseNodeDto findTeamNodeById(Long id, Long teamId) {
        if (nodeRepository.existsByIdAndPageDirectoryTeamId(id, teamId))
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        Node findNode = nodeRepository.findByIdWithLinks(id).orElseThrow(() -> new IllegalArgumentException());

        return ResponseNodeDto.toResponseDto(findNode);
    }
}

    //20자 프리뷰용
//        List<ResponseNodeDto> list = all.stream().map(ResponseNodeDto::toResponseDtoToPreviewList).collect(Collectors.toList());
        //1만자
//        List<ResponseNodeDto> list = all.stream().map(ResponseNodeDto::toResponseDto).collect(Collectors.toList());
        //2단계 조회
//        List<NodePreviewRow> node2StepByPageId = nodeRepository.findNode2StepByPageId(pageId);
//        List<Long> nodeIds = new ArrayList<>(node2StepByPageId.size());
//        for (int i = 0; i < node2StepByPageId.size(); i++) {
//            nodeIds.add(node2StepByPageId.get(i).getId());
//        }
//        List<LinkRow> links =
//                nodeRepository.findLinks2StepByNodeIds(nodeIds);
//
//        Map<Long, Map<Long, String>> notesByNodeId = new HashMap<>();
//
//        for (int i = 0; i < links.size(); i++) {
//            LinkRow row = links.get(i);
//
//            Map<Long, String> notes =
//                    notesByNodeId.computeIfAbsent(
//                            row.getNodeId(),
//                            k -> new HashMap<>()
//                    );
//
//            notes.put(row.getNoteId(), row.getNoteSubject());
//        }
//
//        List<ResponseNodeDto> result =
//                new ArrayList<>(node2StepByPageId.size());
//
//        for (int i = 0; i < node2StepByPageId.size(); i++) {
//            NodePreviewRow n = node2StepByPageId.get(i);
//
//            Map<Long, String> notes =
//                    notesByNodeId.getOrDefault(
//                            n.getId(),
//                            Collections.emptyMap()
//                    );
//
//            ResponseNodeDto dto = ResponseNodeDto.builder()
//                    .id(n.getId())
//                    .x(n.getX())
//                    .y(n.getY())
//                    .subject(n.getSubject())
//                    .content(n.getContentPreview())
//                    .symb(n.getSymb())
//                    .recordDate(n.getRecordDate())
//                    .modifiedAt(n.getModifiedDate())
//                    .pageId(pageId)
//                    .notes(notes)
//                    .build();
//
//            result.add(dto);
//        }


//    //프로젝션용
//    @Transactional(readOnly = true)
//    public List<ResponseNodeDto> findAllProjectionByPageId(Long pageId) {
//        // 1️⃣ Projection 기반 쿼리 실행
//        List<NodePreviewWithNoteProjection> all = nodeRepository.findAllPreviewWithNotesByPageId(pageId);
//
//        // 2️⃣ DTO 변환 (행 폭증 → Node 단위 그룹화)
//        return ResponseNodeDto.fromProjectiontoResponseDto(all);
//    }
//    @Transactional(readOnly = true)
//    public List<ResponseNodeDto> findAllNodePreview(Long pageId) {
//        List<NodePreviewProjection> rows = nodeRepository.findAllPreviewByPageId(pageId);
//        return rows.stream().map(r ->
//                ResponseNodeDto.builder()
//                        .id(r.getId())
//                        .x(r.getX())
//                        .y(r.getY())
//                        .subject(r.getSubject())
//                        .content(r.getContentPreview())  // ← 목록엔 20자 프리뷰만
//                        .pageId(r.getPageId())
//                        .createdAt(r.getCreatedAt())
//                        .modifiedAt(r.getModifiedAt())
//                        .build()
//        ).toList();
//    }


//    @Transactional(readOnly = true)
//    public List<ResponseNodeDto> findAllNodeWithNotesJson(Long pageId) {
////        List<Node> nodes = nodeRepository.findAllNodeWithNotesJson(pageId);
//        List<NodeRowProjection> rows = nodeRepository.findAllNodeRowProjectionByPageId(pageId);
////        return nodeRepository.findAllFetchByPageId(pageId)
////                .stream()
////                .map(ResponseNodeDto::toResponseDto)
////                .toList();
//        return rows.stream().map(r -> {
//            Map<Long,String> notes = parseToMap(r.getNotesJson()); // Jackson으로 파싱
//            return ResponseNodeDto.builder()
//                    .id(r.getId())
//                    .x(r.getX())
//                    .y(r.getY())
//                    .subject(r.getSubject())
//                    .content(r.getContent())           // ← 추가
//                    .symb(r.getSymb())                 // ← 추가
//                    .recordDate(r.getRecordDate())     // ← 추가
//                    .pageId(r.getPageId())
//                    .createdAt(r.getCreatedDate())     // ← 추가
//                    .modifiedAt(r.getModifiedDate())   // ← 추가
//                    .notes(notes)
//                    .build();
//        }).toList();
//
//    }




