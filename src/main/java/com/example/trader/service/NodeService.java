package com.example.trader.service;

import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.entity.Node;
import com.example.trader.entity.Note;
import com.example.trader.entity.Page;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.EdgeRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.repository.NoteRepository;
import com.example.trader.repository.PageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeService {

    private final NodeRepository nodeRepository;
    private final PageRepository pageRepository;
    private final NoteRepository noteRepository;
    private final EdgeRepository edgeRepository;
    private final EntityManager em;
    private final ObjectMapper objectMapper;

    private final NodeCacheService nodeCacheService;
    private final GraphCacheService graphCacheService;

    @Transactional
    public void deleteNode(Long pageId, Long nodeId, Long userId) {
        if (!nodeRepository.existsByIdAndPageIdAndPageUserId(nodeId, pageId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        edgeRepository.deleteByNodeId(nodeId);
        nodeRepository.deleteById(nodeId);

        nodeCacheService.evictPageNodes(pageId);
        graphCacheService.evictGraph(pageId);
    }

    @Transactional
    public void deleteTeamNode(Long teamId, Long graphId, Long nodeId) {
        if (!nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(nodeId, graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        edgeRepository.deleteByNodeId(nodeId);
        nodeRepository.deleteById(nodeId);

        nodeCacheService.evictPageNodes(graphId);
        graphCacheService.evictGraph(graphId);
    }

    @Transactional
    public ResponseNodeDto updateNode(Long pageId, Long nodeId, RequestNodeDto req, Long userId) {
        if (!nodeRepository.existsByIdAndPageIdAndPageUserId(nodeId, pageId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        Node node = nodeRepository.findByIdWithLinks(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("연결된 노트링크가 없습니다"));

        node.updateBasics(req);

        if (req.isNoteIdsOmitted()) {
            // 변경 없음
        } else if (req.isNoteIdsEmptySet()) {
            syncNotes(node, List.of());
        } else {
            syncNotes(node, req.getNoteIds());
        }

        nodeCacheService.evictPageNodes(pageId);
        graphCacheService.evictGraph(pageId);
        return ResponseNodeDto.toResponseDto(node);
    }

    @Transactional
    public ResponseNodeDto updateTeamNode(Long teamId, Long graphId, Long nodeId, Long userId, RequestNodeDto req) {
        if (!nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(nodeId, graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        Node node = nodeRepository.findByIdWithLinks(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("연결된 노트링크가 없습니다"));

        node.updateBasics(req);

        if (req.isNoteIdsOmitted()) {
            // 변경 없음
        } else if (req.isNoteIdsEmptySet()) {
            syncTeamNotesMineOnly(node, List.of(), userId);
        } else {
            syncTeamNotesMineOnly(node, req.getNoteIds(), userId);
        }

        nodeCacheService.evictPageNodes(graphId);
        graphCacheService.evictGraph(graphId);
        return ResponseNodeDto.toResponseDto(node);
    }

    @Transactional
    public void updatePosition(Long pageId, Long nodeId, Long userId, double x, double y) {
        if (!nodeRepository.existsByIdAndPageIdAndPageUserId(nodeId, pageId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        int updated = nodeRepository.updatePosition(nodeId, x, y);
        if (updated == 0) {
            throw new IllegalArgumentException("Node not found or no permission");
        }

        nodeCacheService.evictPageNodes(pageId);
        graphCacheService.evictGraph(pageId);
    }

    @Transactional
    public void updatePositionInTeam(Long teamId, Long graphId, Long nodeId, double x, double y) {
        if (nodeRepository.updatePositionInTeam(teamId, graphId, nodeId, x, y) == 0) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        nodeCacheService.evictPageNodes(graphId);
        graphCacheService.evictGraph(graphId);
    }

    @Transactional
    public ResponseNodeDto createNode(RequestNodeDto dto, Long pageId, Long userId) {
        if (!pageRepository.existsByIdAndUserId(pageId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

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
                .build();

        if (dto.isNoteIdsOmitted()) {
            // 변경 없음
        } else if (dto.isNoteIdsEmptySet()) {
            syncNotes(node, List.of());
        } else {
            syncNotes(node, dto.getNoteIds());
        }

        Node saved = nodeRepository.save(node);

        nodeCacheService.evictPageNodes(pageId);
        graphCacheService.evictGraph(pageId);
        return ResponseNodeDto.toResponseDto(saved);
    }

    @Transactional
    public ResponseNodeDto createTeamNode(RequestNodeDto dto, Long teamId, Long pageId, Long userId) {
        if (!pageRepository.existsByIdAndDirectoryTeamId(pageId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

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
                .build();

        if (dto.isNoteIdsOmitted()) {
            // 변경 없음
        } else if (dto.isNoteIdsEmptySet()) {
            syncTeamNotesMineOnly(node, List.of(), userId);
        } else {
            syncTeamNotesMineOnly(node, dto.getNoteIds(), userId);
        }

        Node saved = nodeRepository.save(node);

        nodeCacheService.evictPageNodes(pageId);
        graphCacheService.evictGraph(pageId);
        return ResponseNodeDto.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ResponseNodeDto> findAllByPageId(Long pageId, Long userId) {
        if (!pageRepository.existsByIdAndUserId(pageId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        return nodeCacheService.getCachedNodesByPageId(pageId);
    }

    @Transactional(readOnly = true)
    public List<ResponseNodeDto> findAllTeamNodesByGraphId(Long teamId, Long graphId) {
        if (!pageRepository.existsByIdAndDirectoryTeamId(graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        return nodeCacheService.getCachedNodesByPageId(graphId);
    }

    @Transactional(readOnly = true)
    public ResponseNodeDto findPersonalNodeById(Long pageId, Long nodeId, Long userId) {
        Node node = nodeRepository.findPersonalNodeWithLinks(nodeId, userId)
                .orElseThrow(() -> new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE));

        if (!node.getPage().getId().equals(pageId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        return ResponseNodeDto.toResponseDto(node);
    }

    @Transactional(readOnly = true)
    public ResponseNodeDto findTeamNodeById(Long graphId, Long nodeId, Long teamId) {
        if (!nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(nodeId, graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        Node findNode = nodeRepository.findByIdWithLinks(nodeId)
                .orElseThrow(IllegalArgumentException::new);

        return ResponseNodeDto.toResponseDto(findNode);
    }

    private void syncNotes(Node node, List<Long> desiredNoteIds) {
        Set<Long> current = node.getNoteLinks().stream()
                .map(l -> l.getNote().getId())
                .collect(Collectors.toSet());

        Set<Long> desired = new HashSet<>(desiredNoteIds);

        Set<Long> toAdd = new HashSet<>(desired);
        toAdd.removeAll(current);

        Set<Long> toRemove = new HashSet<>(current);
        toRemove.removeAll(desired);

        if (!toAdd.isEmpty()) {
            var refs = toAdd.stream()
                    .map(id -> em.getReference(Note.class, id))
                    .toList();
            node.attachAll(refs);
        }

        if (!toRemove.isEmpty()) {
            var refs = toRemove.stream()
                    .map(id -> em.getReference(Note.class, id))
                    .toList();
            node.detachAll(refs);
        }
    }

    private void syncTeamNotesMineOnly(Node node, List<Long> desiredNoteIds, Long userId) {
        Set<Long> desired = new HashSet<>(desiredNoteIds == null ? List.of() : desiredNoteIds);

        Set<Long> currentMine = node.getNoteLinks().stream()
                .filter(link -> link.getNote().getUser().getId().equals(userId))
                .map(link -> link.getNote().getId())
                .collect(Collectors.toSet());

        Set<Long> toAdd = new HashSet<>(desired);
        toAdd.removeAll(currentMine);

        Set<Long> toRemove = new HashSet<>(currentMine);
        toRemove.removeAll(desired);

        if (!toAdd.isEmpty()) {
            List<Long> allowedIds = noteRepository.findIdsByUserIdAndIdIn(userId, toAdd);
            if (allowedIds.size() != toAdd.size()) {
                throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
            }

            var refs = allowedIds.stream()
                    .map(id -> em.getReference(Note.class, id))
                    .toList();
            node.attachAll(refs);
        }

        if (!toRemove.isEmpty()) {
            var refs = toRemove.stream()
                    .map(id -> em.getReference(Note.class, id))
                    .toList();
            node.detachAll(refs);
        }
    }
}