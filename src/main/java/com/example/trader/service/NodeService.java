package com.example.trader.service;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.map.CreateNodeRequest;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.dto.map.ResponseNodeSummaryDto;
import com.example.trader.dto.map.UpdatePersonalNodeContentRequest;
import com.example.trader.dto.map.UpdateTeamNodeContentRequest;
import com.example.trader.entity.Node;
import com.example.trader.entity.NodeChangePlan;
import com.example.trader.entity.NodeChangeResult;
import com.example.trader.entity.NodeContentField;
import com.example.trader.entity.NodeVersionPolicy;
import com.example.trader.entity.NodeHistory;
import com.example.trader.entity.Note;
import com.example.trader.entity.Page;
import com.example.trader.exception.BaseException;
import com.example.trader.exception.ConcurrentNodeUpdateException;
import com.example.trader.exception.NodeConflictException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.observability.ObservedLog;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.realtime.outbox.TransactionalReliableEventService;
import com.example.trader.repository.*;
import com.example.trader.service.event.TeamNodeChangeCommittedEvent;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
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
    private final NodeChangeRequestValidator changeRequestValidator;
    private final NodeConflictValidator  conflictValidator;
    private final TransactionalReliableEventService transactionalReliableEventService;
    private final ApplicationEventPublisher eventPublisher;

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
    public ResponseNodeDto updateNode(
            Long pageId,
            Long nodeId,
            UpdatePersonalNodeContentRequest req,
            Long userId
    ) {
        if (!nodeRepository.existsByIdAndPageIdAndPageUserId(nodeId, pageId, userId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        Node node = nodeRepository.findByIdWithLinks(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("연결된 노트링크가 없습니다"));

        Set<Long> currentNoteIds = allNoteIds(node);
        NodeChangePlan plan = changeRequestValidator.validatePersonal(req, node, currentNoteIds);
        if (plan.isNoOp()) {
            return ResponseNodeDto.from(node);
        }

        node.applyContentChange(
                plan.effectiveChange(),
                resolvePersonalNotes(plan),
                currentNoteIds,
                NodeVersionPolicy.PRESERVE
        );

        nodeCacheService.evictPageNodes(pageId);
        graphCacheService.evictGraph(pageId);
        return ResponseNodeDto.from(node);
    }

    @ObservedLog(domain = "node", api = "updateTeamNode")
    @Transactional
    public ResponseNodeDto updateTeamNode(
            Long teamId,
            Long graphId,
            Long nodeId,
            Long userId,
            UpdateTeamNodeContentRequest req
    ) {
        if (!nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(nodeId, graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        Node node = nodeRepository.findByIdWithLinks(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("연결된 노트링크가 없습니다"));

        Set<Long> replaceableNoteIds = teamNoteIdsOwnedBy(node, userId);
        NodeChangePlan plan = changeRequestValidator.validateTeam(req, node, replaceableNoteIds);

        log.info(
                "node update request teamId={} graphId={} nodeId={} userId={} baseVersion={} requestedFields={} effectiveFields={}",
                teamId,
                graphId,
                nodeId,
                userId,
                req.getBaseVersion(),
                plan.requestedFields(),
                plan.effectiveChange().fieldNames()
        );

        // 동일 값 재요청은 충돌 검사와 모든 부수효과가 없는 멱등 성공이다.
        if (plan.isNoOp()) {
            return ResponseNodeDto.from(node);
        }

        ConflictResult conflict = conflictValidator.validateContent(
                teamId,
                graphId,
                nodeId,
                node,
                req.getBaseVersion(),
                plan.effectiveChange()
        );
        if (conflict.isConflict()) {
            throw new NodeConflictException(conflict);
        }

        NodeChangeResult changeResult = node.applyContentChange(
                plan.effectiveChange(),
                resolveTeamNotes(plan, userId),
                replaceableNoteIds,
                NodeVersionPolicy.ADVANCE_ON_CHANGE
        );
        List<String> changedFields = changeResult.appliedFields();
        int newVersion = changeResult.newVersion();

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

        eventPublisher.publishEvent(new TeamNodeChangeCommittedEvent(
                teamId,
                graphId,
                nodeId,
                userId,
                newVersion,
                changedFields
        ));
        flushWithOptimisticConcurrencyCheck();
        return ResponseNodeDto.from(node);
    }

    private void flushWithOptimisticConcurrencyCheck() {
        try {
            nodeRepository.flush();
        } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
            throw new ConcurrentNodeUpdateException(exception);
        }
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
    public ResponseNodeDto createNode(CreateNodeRequest dto, Long pageId, Long userId) {
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
        } else if (dto.getNoteIds().isEmpty()) {
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
    public ResponseNodeDto createTeamNode(CreateNodeRequest dto, Long teamId, Long pageId, Long userId) {
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
        } else if (dto.getNoteIds().isEmpty()) {
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

    private Set<Long> allNoteIds(Node node) {
        return node.getNoteLinks().stream()
                .map(link -> link.getNote().getId())
                .collect(Collectors.toSet());
    }

    private Set<Long> teamNoteIdsOwnedBy(Node node, Long userId) {
        return node.getNoteLinks().stream()
                .filter(link -> link.getNote().getUser().getId().equals(userId))
                .map(link -> link.getNote().getId())
                .collect(Collectors.toSet());
    }

    private List<Note> resolvePersonalNotes(NodeChangePlan plan) {
        if (!plan.effectiveChange().contains(NodeContentField.NOTE_IDS)) {
            return List.of();
        }
        return plan.effectiveChange().noteIds().stream()
                .map(id -> em.getReference(Note.class, id))
                .toList();
    }

    private List<Note> resolveTeamNotes(NodeChangePlan plan, Long userId) {
        if (!plan.effectiveChange().contains(NodeContentField.NOTE_IDS)) {
            return List.of();
        }

        Set<Long> requestedIds = new HashSet<>(plan.effectiveChange().noteIds());
        if (requestedIds.isEmpty()) {
            return List.of();
        }
        List<Long> allowedIds = noteRepository.findIdsByUserIdAndIdIn(userId, requestedIds);
        if (allowedIds.size() != requestedIds.size()) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }
        return allowedIds.stream()
                .map(id -> em.getReference(Note.class, id))
                .toList();
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
                null,
                null,
                version
        );

        transactionalReliableEventService.stage(
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
