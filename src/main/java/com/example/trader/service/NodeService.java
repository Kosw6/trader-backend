package com.example.trader.service;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.ResponseNoteDto;
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
import com.example.trader.service.event.NodeChange;
import com.example.trader.service.event.NodeChangeEventStager;
import com.example.trader.repository.*;
import com.example.trader.service.event.TeamNodeChangeCommittedEvent;
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

import java.util.Collection;
import java.util.LinkedHashSet;
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
    private final NodeNoteLinkRepository nodeNoteLinkRepository;
    private final NodeChangeRequestValidator changeRequestValidator;
    private final NodeConflictValidator  conflictValidator;
    private final NodeChangeEventStager nodeChangeEventStager;
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

        nodeChangeEventStager.stage(NodeChange.deleted(teamId, graphId, nodeId));
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
        List<Note> desiredNotes = resolveRequestedNotes(plan, userId);
        if (plan.isNoOp()) {
            return ResponseNodeDto.from(node);
        }

        node.applyContentChange(
                plan.effectiveChange(),
                desiredNotes,
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
        List<Note> desiredNotes = resolveRequestedNotes(plan, userId);

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

        ConflictResult conflict = conflictValidator.validateContent(
                teamId,
                graphId,
                nodeId,
                node,
                req.getBaseVersion(),
                plan.requestedChange(),
                plan.effectiveChange(),
                userId
        );
        if (conflict.requiresUserReview()) {
            throw new NodeConflictException(conflict);
        }

        // 최신 문맥 검토까지 통과한 동일 값 재요청만 부수효과 없는 멱등 성공이다.
        if (plan.isNoOp()) {
            return ResponseNodeDto.from(node, userId);
        }

        NodeChangeResult changeResult = node.applyContentChange(
                plan.effectiveChange(),
                desiredNotes,
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

        nodeChangeEventStager.stage(NodeChange.updated(
                teamId, graphId, nodeId, userId, changedFields, newVersion));

        eventPublisher.publishEvent(new TeamNodeChangeCommittedEvent(
                teamId,
                graphId,
                nodeId,
                userId,
                newVersion,
                changedFields
        ));
        flushWithOptimisticConcurrencyCheck();
        return ResponseNodeDto.from(node, userId);
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

        if (!dto.isNoteIdsOmitted()) {
            node.replaceNoteLinks(resolveOwnedNotes(dto.getNoteIds(), userId), Set.of());
        }

        Node saved = nodeRepository.save(node);

        nodeCacheService.evictPageNodes(pageId);
        graphCacheService.evictGraph(pageId);
        return ResponseNodeDto.from(saved, userId);
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

        if (!dto.isNoteIdsOmitted()) {
            node.replaceNoteLinks(resolveOwnedNotes(dto.getNoteIds(), userId), Set.of());
        }

        Node saved = nodeRepository.save(node);

        nodeCacheService.evictPageNodes(pageId);
        graphCacheService.evictGraph(pageId);
        nodeChangeEventStager.stage(NodeChange.created(
                teamId, pageId, saved.getId(), userId, saved.getVersion()));


        return ResponseNodeDto.from(saved, userId);
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
    public ResponseNodeDto findTeamNodeById(Long graphId, Long nodeId, Long teamId, Long userId) {
        if (!nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(nodeId, graphId, teamId)) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        Node findNode = nodeRepository.findByIdWithLinks(nodeId)
                .orElseThrow(IllegalArgumentException::new);

        return ResponseNodeDto.from(findNode, userId);
    }

    @Transactional(readOnly = true)
    public ResponseNoteDto findTeamLinkedNote(
            Long teamId, Long graphId, Long nodeId, Long noteId
    ) {
        return nodeNoteLinkRepository
                .findLinkedNoteInTeamGraph(teamId, graphId, nodeId, noteId)
                .map(link -> ResponseNoteDto.fromEntity(link.getNote()))
                .orElseThrow(() -> new BaseException(BaseResponseStatus.INVALID_NOTE));
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

    private List<Note> resolveRequestedNotes(NodeChangePlan plan, Long userId) {
        if (!plan.requestedChange().contains(NodeContentField.NOTE_IDS)) {
            return List.of();
        }
        return resolveOwnedNotes(plan.requestedChange().noteIds(), userId);
    }

    private List<Note> resolveOwnedNotes(Collection<Long> noteIds, Long userId) {
        Set<Long> requestedIds = new LinkedHashSet<>(noteIds == null ? List.of() : noteIds);
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

}
