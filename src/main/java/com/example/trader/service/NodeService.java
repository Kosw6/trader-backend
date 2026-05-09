package com.example.trader.service;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.map.RequestNodeDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.dto.map.ResponseNodeSummaryDto;
import com.example.trader.entity.Node;
import com.example.trader.entity.NodeHistory;
import com.example.trader.entity.Note;
import com.example.trader.entity.Page;
import com.example.trader.exception.BaseException;
import com.example.trader.exception.NodeConflictException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.observability.ObservedLog;
import com.example.trader.realtime.RealtimePublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.repository.*;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import com.example.trader.ws.raw.event.NodeChangedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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

    private final NodeCacheService       nodeCacheService;
    private final GraphCacheService      graphCacheService;
    private final NodeHistoryRepository  nodeHistoryRepository;
    private final NodeConflictValidator  conflictValidator;
    private final NodeEditSessionService editSessionService;
    private final RealtimePublisher realtimePublisher;

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

        publishNodeChanged(
                teamId,
                graphId,
                nodeId,
                null,
                "NODE_DELETED",
                List.of("node"),
                null,
                null,
                null
        );
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
        return ResponseNodeDto.from(node);
    }

    @ObservedLog(domain = "node", api = "updateTeamNode")
    @Transactional
    public ResponseNodeDto updateTeamNode(Long teamId, Long graphId, Long nodeId, Long userId, RequestNodeDto req) {
        if (!nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(nodeId, graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        log.info(
                "node update request teamId={} graphId={} nodeId={} userId={} baseVersion={} force={} dirtyFields={}",
                teamId,
                graphId,
                nodeId,
                userId,
                req.getBaseVersion(),
                req.isForce(),
                conflictValidator.extractChangedFields(req)
        );

        Node node = nodeRepository.findByIdWithLinks(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("연결된 노트링크가 없습니다"));

        log.info(
                "node update request teamId={} graphId={} nodeId={} userId={} baseVersion={} force={} dirtyFields={}",
                teamId,
                graphId,
                nodeId,
                userId,
                req.getBaseVersion(),
                req.isForce(),
                conflictValidator.extractChangedFields(req)
        );

        // ── 충돌 검증 (force=true 이면 skip) ────────────────────────────────
        if (!req.isForce()) {
            ConflictResult conflict = conflictValidator.validate(teamId, graphId, nodeId, node, req);
            if (conflict.isConflict()) {
                throw new NodeConflictException(conflict);
            }
        }

        // 변경될 필드 목록 (히스토리 저장용) — updateBasics 이전에 추출
        List<String> changedFields = conflictValidator.extractChangedFields(req);

        node.updateBasics(req);

        if (req.isNoteIdsOmitted()) {
            // 변경 없음
        } else if (req.isNoteIdsEmptySet()) {
            syncTeamNotesMineOnly(node, List.of(), userId);
        } else {
            syncTeamNotesMineOnly(node, req.getNoteIds(), userId);
        }

        // ── 버전 증가 ────────────────────────────────────────────────────────
        node.incrementVersion();
        int newVersion = node.getVersion();

        // ── DB 버전 히스토리 저장 ────────────────────────────────────────────
        if (!changedFields.isEmpty()) {
            String fieldsJson = toJson(changedFields);
            nodeHistoryRepository.save(NodeHistory.builder()
                    .nodeId(nodeId)
                    .teamId(teamId)
                    .graphId(graphId)
                    .version(newVersion)
                    .changedBy(userId)
                    .changedFields(fieldsJson)
                    .changedAt(java.time.LocalDateTime.now())
                    .build());

            // ── Redis 버전 힌트 저장 (TTL 1시간) ────────────────────────────
            editSessionService.saveVersionHint(teamId, graphId, nodeId, newVersion, changedFields, userId);
        }

        // ── 편집 세션 정리 ───────────────────────────────────────────────────
        editSessionService.endEditSession(teamId, graphId, nodeId, userId);

        nodeCacheService.evictPageNodes(graphId);
        graphCacheService.evictGraph(graphId);

        publishNodeChanged(
                teamId,
                graphId,
                nodeId,
                userId,
                "NODE_UPDATED",
                changedFields,
                newVersion,
                null,
                null
        );

        log.info(
                "node update committed teamId={} graphId={} nodeId={} userId={} newVersion={} changedFields={} redisHintSaved={}",
                teamId,
                graphId,
                nodeId,
                userId,
                newVersion,
                changedFields,
                !changedFields.isEmpty()
        );
        return ResponseNodeDto.from(node);
    }

    /** List를 JSON 문자열로 직렬화 (히스토리 저장용) */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("[NodeService] toJson failed: {}", e.getMessage());
            return "[]";
        }
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
        publishNodeChanged(
                teamId,
                graphId,
                nodeId,
                null,
                "NODE_POSITION_UPDATED",
                List.of("x", "y"),
                null,
                x,
                y
        );
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
        return ResponseNodeDto.from(saved);
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
        publishNodeChanged(
                teamId,
                pageId,
                saved.getId(),
                userId,
                "NODE_CREATED",
                List.of("node"),
                saved.getVersion(),
                null,
                null
        );


        return ResponseNodeDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ResponseNodeSummaryDto> findAllByPageId(Long pageId, Long userId) {
        if (!pageRepository.existsByIdAndUserId(pageId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }
//        return nodeRepository.findAllFetchByPageId(pageId)
//                .stream()
//                .map(ResponseNodeDto::toResponseDtoToPreviewList)
//                .toList();
        return nodeCacheService.getCachedNodesByPageId(pageId);
    }

    @Transactional(readOnly = true)
    public List<ResponseNodeSummaryDto> findAllTeamNodesByGraphId(Long teamId, Long graphId) {
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

        return ResponseNodeDto.from(node);
    }

    @Transactional(readOnly = true)
    public ResponseNodeDto findTeamNodeById(Long graphId, Long nodeId, Long teamId) {
        if (!nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(nodeId, graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        Node findNode = nodeRepository.findByIdWithLinks(nodeId)
                .orElseThrow(IllegalArgumentException::new);

        return ResponseNodeDto.from(findNode);
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

    private void publishNodeChanged(
            Long teamId,
            Long graphId,
            Long nodeId,
            Long userId,
            String eventType,
            List<String> changedFields,
            Integer version,
            Double x,
            Double y
    ) {
        RawCursorMessage msg = new RawCursorMessage(
                "__CONTROL__",
                eventType,
                teamId,
                graphId,
                userId,
                null,
                nodeId,
                x != null ? x : 0,
                y != null ? y : 0,
                System.currentTimeMillis(),
                changedFields,
                version,
                null
        );

        realtimePublisher.publish(
                RealtimeEnvelope.builder()
                        .type(RealtimeType.RELIABLE)
                        .subType(toRealtimeSubType(eventType))
                        .teamId(teamId)
                        .graphId(graphId)
                        .payload(msg)
                        .build()
        );
    }

    private RealtimeSubType toRealtimeSubType(String eventType) {
        return switch (eventType) {
            case "NODE_CREATED" -> RealtimeSubType.NODE_CREATED;
            case "NODE_UPDATED" -> RealtimeSubType.NODE_UPDATED;
            case "NODE_DELETED" -> RealtimeSubType.NODE_DELETED;
            case "NODE_POSITION_UPDATED" -> RealtimeSubType.NODE_POSITION_UPDATED;
            default -> RealtimeSubType.CONTROL;
        };
    }
}