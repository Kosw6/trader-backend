package com.example.trader.service;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.map.CreateNodeRequest;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.dto.map.UpdatePersonalNodeContentRequest;
import com.example.trader.dto.map.UpdateTeamNodeContentRequest;
import com.example.trader.entity.Node;
import com.example.trader.entity.NodeHistory;
import com.example.trader.entity.NodeNoteLink;
import com.example.trader.entity.Note;
import com.example.trader.entity.Role;
import com.example.trader.entity.User;
import com.example.trader.exception.InvalidNodeChangeRequestException;
import com.example.trader.exception.NodeChangeErrorCode;
import com.example.trader.exception.NodeHistoryCorruptedException;
import com.example.trader.exception.NodeHistoryCorruptionReason;
import com.example.trader.exception.NodeConflictException;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.outbox.TransactionalReliableEventService;
import com.example.trader.repository.EdgeRepository;
import com.example.trader.repository.NodeHistoryRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.repository.NoteRepository;
import com.example.trader.repository.PageRepository;
import com.example.trader.service.event.TeamNodeChangeCommittedEvent;
import com.example.trader.ws.raw.dto.RawCursorMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceContentUpdateTest {

    @Mock NodeRepository nodeRepository;
    @Mock PageRepository pageRepository;
    @Mock NoteRepository noteRepository;
    @Mock EdgeRepository edgeRepository;
    @Mock EntityManager entityManager;
    @Mock NodeCacheService nodeCacheService;
    @Mock GraphCacheService graphCacheService;
    @Mock NodeHistoryRepository nodeHistoryRepository;
    @Mock com.example.trader.repository.NodeNoteLinkRepository nodeNoteLinkRepository;
    @Mock NodeConflictValidator conflictValidator;
    @Mock TransactionalReliableEventService reliableEventService;
    @Mock ApplicationEventPublisher eventPublisher;

    private NodeService nodeService;

    @BeforeEach
    void setUp() {
        nodeService = new NodeService(
                nodeRepository,
                pageRepository,
                noteRepository,
                edgeRepository,
                entityManager,
                new ObjectMapper(),
                nodeCacheService,
                graphCacheService,
                nodeHistoryRepository,
                nodeNoteLinkRepository,
                new NodeChangeRequestValidator(),
                conflictValidator,
                reliableEventService,
                eventPublisher
        );
    }

    @Test
    @DisplayName("정상 팀 변경은 적용 필드와 버전을 History, Outbox, 커밋 후 이벤트에 동일하게 전달")
    void successful_change_uses_one_applied_field_contract() {
        Node node = node();
        UpdateTeamNodeContentRequest request = teamSubjectRequest("새 제목", 3, false);
        when(nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(30L, 20L, 10L))
                .thenReturn(true);
        when(nodeRepository.findByIdWithLinks(30L)).thenReturn(Optional.of(node));
        when(conflictValidator.validateContent(any(), any(), any(), any(), any(Integer.class), any(), any(), any()))
                .thenReturn(ConflictResult.pass(3));

        ResponseNodeDto response = nodeService.updateTeamNode(10L, 20L, 30L, 40L, request);

        assertThat(response.getSubject()).isEqualTo("새 제목");
        assertThat(response.getVersion()).isEqualTo(4);

        ArgumentCaptor<NodeHistory> historyCaptor = ArgumentCaptor.forClass(NodeHistory.class);
        verify(nodeHistoryRepository).save(historyCaptor.capture());
        NodeHistory history = historyCaptor.getValue();
        assertThat(history.getVersion()).isEqualTo(4);
        assertThat(history.getChangedFields()).isEqualTo("[\"subject\"]");

        verify(nodeCacheService, never()).evictPageNodes(any());
        verify(graphCacheService, never()).evictGraph(any());

        ArgumentCaptor<RealtimeEnvelope> envelopeCaptor = ArgumentCaptor.forClass(RealtimeEnvelope.class);
        verify(reliableEventService).stage(envelopeCaptor.capture());
        RawCursorMessage payload = (RawCursorMessage) envelopeCaptor.getValue().getPayload();
        assertThat(payload.version()).isEqualTo(4);
        assertThat(payload.fields()).containsExactly("subject");

        ArgumentCaptor<TeamNodeChangeCommittedEvent> eventCaptor =
                ArgumentCaptor.forClass(TeamNodeChangeCommittedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().version()).isEqualTo(4);
        assertThat(eventCaptor.getValue().changedFields()).containsExactly("subject");
    }

    @Test
    @DisplayName("같은 값 재요청은 version과 모든 부수효과를 만들지 않음")
    void no_op_has_no_side_effects() {
        Node node = node();
        UpdateTeamNodeContentRequest request = teamSubjectRequest("기존 제목", 3, false);
        when(nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(30L, 20L, 10L))
                .thenReturn(true);
        when(nodeRepository.findByIdWithLinks(30L)).thenReturn(Optional.of(node));
        when(conflictValidator.validateContent(any(), any(), any(), any(), any(Integer.class), any(), any(), any()))
                .thenReturn(ConflictResult.pass(3));

        ResponseNodeDto response = nodeService.updateTeamNode(10L, 20L, 30L, 40L, request);

        assertThat(response.getVersion()).isEqualTo(3);
        verify(conflictValidator).validateContent(any(), any(), any(), any(), any(Integer.class), any(), any(), any());
        verify(nodeHistoryRepository, never()).save(any());
        verify(nodeCacheService, never()).evictPageNodes(any());
        verify(graphCacheService, never()).evictGraph(any());
        verify(reliableEventService, never()).stage(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("force는 본문과 dirtyFields 불일치 계약을 우회하지 못함")
    void force_does_not_bypass_request_contract() {
        Node node = node();
        UpdateTeamNodeContentRequest request = teamSubjectRequest("새 제목", 3, true);
        request.setContent("선언하지 않은 내용");
        when(nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(30L, 20L, 10L))
                .thenReturn(true);
        when(nodeRepository.findByIdWithLinks(30L)).thenReturn(Optional.of(node));

        assertThatThrownBy(() -> nodeService.updateTeamNode(10L, 20L, 30L, 40L, request))
                .isInstanceOfSatisfying(
                        InvalidNodeChangeRequestException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(NodeChangeErrorCode.UNDECLARED_FIELD_VALUE)
                );

        assertThat(node.getSubject()).isEqualTo("기존 제목");
        assertThat(node.getContent()).isEqualTo("기존 내용");
        assertThat(node.getVersion()).isEqualTo(3);
        verify(conflictValidator, never()).validateContent(any(), any(), any(), any(), any(Integer.class), any(), any(), any());
        verify(nodeHistoryRepository, never()).save(any());
        verify(reliableEventService, never()).stage(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("구버전 force 값도 업무 충돌과 CAS 계약을 우회하지 못함")
    void force_does_not_bypass_business_conflict() {
        Node node = node();
        UpdateTeamNodeContentRequest request = teamSubjectRequest("강제 제목", 1, true);
        when(nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(30L, 20L, 10L))
                .thenReturn(true);
        when(nodeRepository.findByIdWithLinks(30L)).thenReturn(Optional.of(node));
        when(conflictValidator.validateContent(any(), any(), any(), any(), any(Integer.class), any(), any(), any()))
                .thenReturn(ConflictResult.conflict(
                        1,
                        3,
                        List.of("subject"),
                        java.util.Map.of("subject", "기존 제목"),
                        java.util.Map.of("subject", "강제 제목")
                ));

        assertThatThrownBy(() -> nodeService.updateTeamNode(10L, 20L, 30L, 40L, request))
                .isInstanceOf(NodeConflictException.class);

        assertThat(node.getSubject()).isEqualTo("기존 제목");
        assertThat(node.getVersion()).isEqualTo(3);
        verify(conflictValidator).validateContent(any(), any(), any(), any(), any(Integer.class), any(), any(), any());
        verify(nodeHistoryRepository, never()).save(any());
        verify(reliableEventService, never()).stage(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("force는 손상된 History를 우회하지 않고 어떤 상태도 변경하지 않음")
    void force_does_not_bypass_corrupted_history() {
        Node node = node();
        UpdateTeamNodeContentRequest request = teamSubjectRequest("강제 제목", 1, true);
        NodeHistory corruptedHistory = NodeHistory.builder()
                .nodeId(30L)
                .version(2)
                .changedFields("[]")
                .build();
        NodeHistoryCorruptedException failure = new NodeHistoryCorruptedException(
                corruptedHistory,
                NodeHistoryCorruptionReason.EMPTY_CHANGED_FIELDS
        );

        when(nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(30L, 20L, 10L))
                .thenReturn(true);
        when(nodeRepository.findByIdWithLinks(30L)).thenReturn(Optional.of(node));
        when(conflictValidator.validateContent(any(), any(), any(), any(), any(Integer.class), any(), any(), any()))
                .thenThrow(failure);

        assertThatThrownBy(() -> nodeService.updateTeamNode(10L, 20L, 30L, 40L, request))
                .isSameAs(failure);

        assertThat(node.getSubject()).isEqualTo("기존 제목");
        assertThat(node.getVersion()).isEqualTo(3);
        verify(nodeHistoryRepository, never()).save(any());
        verify(nodeCacheService, never()).evictPageNodes(any());
        verify(graphCacheService, never()).evictGraph(any());
        verify(reliableEventService, never()).stage(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("noteIds 빈 배열은 팀 사용자가 소유한 기존 연결을 해제하고 noteIds를 기록")
    void empty_note_ids_clear_owned_links_and_are_recorded() {
        User owner = user(40L);
        Note note = Note.builder().id(50L).user(owner).subject("연결 노트").build();
        Node node = node();
        node.replaceNoteLinks(List.of(
                note,
                Note.builder().id(60L).user(user(41L)).subject("다른 사용자 노트").build()
        ), java.util.Set.of());
        UpdateTeamNodeContentRequest request = new UpdateTeamNodeContentRequest();
        request.setNoteIds(List.of());
        request.setDirtyFields(List.of("noteIds"));
        request.setBaseVersion(3);

        when(nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(30L, 20L, 10L))
                .thenReturn(true);
        when(nodeRepository.findByIdWithLinks(30L)).thenReturn(Optional.of(node));
        when(conflictValidator.validateContent(any(), any(), any(), any(), any(Integer.class), any(), any(), any()))
                .thenReturn(ConflictResult.pass(3));

        nodeService.updateTeamNode(10L, 20L, 30L, 40L, request);

        assertThat(node.getNoteLinks()).extracting(link -> link.getNote().getId())
                .containsExactly(60L);
        ArgumentCaptor<NodeHistory> historyCaptor = ArgumentCaptor.forClass(NodeHistory.class);
        verify(nodeHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getChangedFields()).isEqualTo("[\"noteIds\"]");
        ArgumentCaptor<TeamNodeChangeCommittedEvent> eventCaptor =
                ArgumentCaptor.forClass(TeamNodeChangeCommittedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().changedFields()).containsExactly("noteIds");
    }

    @Test
    @DisplayName("팀 사용자는 다른 사용자의 노트를 자신의 링크 집합에 추가할 수 없음")
    void cannot_attach_another_users_note() {
        Node node = node();
        UpdateTeamNodeContentRequest request = new UpdateTeamNodeContentRequest();
        request.setNoteIds(List.of(60L));
        request.setDirtyFields(List.of("noteIds"));
        request.setBaseVersion(3);
        when(nodeRepository.existsByIdAndPageIdAndPageDirectoryTeamId(30L, 20L, 10L))
                .thenReturn(true);
        when(nodeRepository.findByIdWithLinks(30L)).thenReturn(Optional.of(node));
        when(noteRepository.findIdsByUserIdAndIdIn(40L, java.util.Set.of(60L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> nodeService.updateTeamNode(10L, 20L, 30L, 40L, request))
                .isInstanceOf(com.example.trader.exception.BaseException.class);
        assertThat(node.getNoteLinks()).isEmpty();
        verify(nodeHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("개인 내용 수정에서 noteIds 누락은 기존 연결을 보존")
    void omitted_note_ids_preserve_personal_links() {
        Note note = Note.builder().id(50L).subject("연결 노트").build();
        Node node = node();
        node.replaceNoteLinks(List.of(note), java.util.Set.of());
        UpdatePersonalNodeContentRequest request = new UpdatePersonalNodeContentRequest();
        request.setSubject("개인 새 제목");

        when(nodeRepository.existsByIdAndPageIdAndPageUserId(30L, 20L, 40L)).thenReturn(true);
        when(nodeRepository.findByIdWithLinks(30L)).thenReturn(Optional.of(node));

        ResponseNodeDto response = nodeService.updateNode(20L, 30L, request, 40L);

        assertThat(response.getSubject()).isEqualTo("개인 새 제목");
        assertThat(node.getNoteLinks()).hasSize(1);
        assertThat(node.getVersion()).isEqualTo(3);
        verify(nodeCacheService).evictPageNodes(20L);
        verify(graphCacheService).evictGraph(20L);
    }

    @Test
    @DisplayName("개인 Node 수정은 타인 소유 Note 연결을 변경 전에 거절")
    void personal_update_rejects_another_users_note() {
        Node node = node();
        UpdatePersonalNodeContentRequest request = new UpdatePersonalNodeContentRequest();
        request.setNoteIds(List.of(60L));
        when(nodeRepository.existsByIdAndPageIdAndPageUserId(30L, 20L, 40L)).thenReturn(true);
        when(nodeRepository.findByIdWithLinks(30L)).thenReturn(Optional.of(node));
        when(noteRepository.findIdsByUserIdAndIdIn(40L, java.util.Set.of(60L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> nodeService.updateNode(20L, 30L, request, 40L))
                .isInstanceOf(com.example.trader.exception.BaseException.class);

        assertThat(node.getNoteLinks()).isEmpty();
        verify(nodeCacheService, never()).evictPageNodes(any());
        verify(graphCacheService, never()).evictGraph(any());
    }

    @Test
    @DisplayName("개인 Node의 동일 값 no-op 요청도 Note 소유권 검증을 우회하지 못함")
    void personal_no_op_does_not_bypass_note_ownership() {
        Node node = node();
        node.replaceNoteLinks(List.of(
                Note.builder().id(60L).user(user(41L)).subject("타인 노트").build()
        ), java.util.Set.of());
        UpdatePersonalNodeContentRequest request = new UpdatePersonalNodeContentRequest();
        request.setNoteIds(List.of(60L));
        when(nodeRepository.existsByIdAndPageIdAndPageUserId(30L, 20L, 40L)).thenReturn(true);
        when(nodeRepository.findByIdWithLinks(30L)).thenReturn(Optional.of(node));
        when(noteRepository.findIdsByUserIdAndIdIn(40L, java.util.Set.of(60L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> nodeService.updateNode(20L, 30L, request, 40L))
                .isInstanceOf(com.example.trader.exception.BaseException.class);

        assertThat(node.getNoteLinks()).hasSize(1);
        verify(nodeCacheService, never()).evictPageNodes(any());
    }

    @Test
    @DisplayName("개인 Node 생성은 타인 소유 Note 연결을 저장 전에 거절")
    void personal_create_rejects_another_users_note() {
        CreateNodeRequest request = new CreateNodeRequest();
        request.setX(10.0);
        request.setY(20.0);
        request.setSubject("개인 노드");
        request.setContent("내용");
        request.setNoteIds(List.of(60L));
        when(pageRepository.existsByIdAndUserId(20L, 40L)).thenReturn(true);
        when(pageRepository.findById(20L)).thenReturn(Optional.of(
                com.example.trader.entity.Page.builder().id(20L).build()));
        when(noteRepository.findIdsByUserIdAndIdIn(40L, java.util.Set.of(60L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> nodeService.createNode(request, 20L, 40L))
                .isInstanceOf(com.example.trader.exception.BaseException.class);

        verify(nodeRepository, never()).save(any());
        verify(nodeCacheService, never()).evictPageNodes(any());
        verify(graphCacheService, never()).evictGraph(any());
    }

    @Test
    @DisplayName("팀 연결 노트 조회는 지정한 그래프의 노드에 실제 연결된 노트만 반환")
    void find_team_linked_note_uses_scoped_link_query() {
        User owner = user(41L);
        Note note = Note.builder().id(50L).user(owner).teamId(10L)
                .subject("공유 노트").content("공유 내용").build();
        Node linkedNode = node();
        NodeNoteLink link = NodeNoteLink.of(linkedNode, note);
        when(nodeNoteLinkRepository.findLinkedNoteInTeamGraph(10L, 20L, 30L, 50L))
                .thenReturn(Optional.of(link));

        var response = nodeService.findTeamLinkedNote(10L, 20L, 30L, 50L);

        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getSubject()).isEqualTo("공유 노트");
        assertThat(response.getUsername()).isEqualTo("사용자");
    }

    @Test
    @DisplayName("다른 그래프 또는 노드의 노트는 팀 연결 노트 API로 조회할 수 없음")
    void find_team_linked_note_rejects_unscoped_note() {
        when(nodeNoteLinkRepository.findLinkedNoteInTeamGraph(10L, 20L, 30L, 50L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> nodeService.findTeamLinkedNote(10L, 20L, 30L, 50L))
                .isInstanceOf(com.example.trader.exception.BaseException.class);
    }

    @Test
    @DisplayName("팀 노드 응답은 모든 링크를 노출하되 요청자 소유 링크만 편집 가능")
    void team_node_response_marks_only_requester_links_editable() {
        Node node = node();
        node.replaceNoteLinks(List.of(
                Note.builder().id(50L).user(user(40L)).subject("내 노트").build(),
                Note.builder().id(60L).user(user(41L)).subject("다른 노트").build()
        ), java.util.Set.of());

        ResponseNodeDto response = ResponseNodeDto.from(node, 40L);

        assertThat(response.getLinkedNotes()).hasSize(2);
        assertThat(response.getLinkedNotes()).filteredOn(note -> note.noteId().equals(50L))
                .allMatch(ResponseNodeDto.LinkedNoteDto::editable);
        assertThat(response.getLinkedNotes()).filteredOn(note -> note.noteId().equals(60L))
                .noneMatch(ResponseNodeDto.LinkedNoteDto::editable);
    }

    private UpdateTeamNodeContentRequest teamSubjectRequest(
            String subject,
            int baseVersion,
            boolean force
    ) {
        UpdateTeamNodeContentRequest request = new UpdateTeamNodeContentRequest();
        request.setSubject(subject);
        request.setDirtyFields(List.of("subject"));
        request.setBaseVersion(baseVersion);
        request.setForce(force);
        return request;
    }

    private Node node() {
        return Node.builder()
                .id(30L)
                .subject("기존 제목")
                .content("기존 내용")
                .symb("AAPL")
                .recordDate(LocalDate.of(2026, 9, 1))
                .version(3)
                .build();
    }

    private User user(Long id) {
        return User.builder()
                .id(id)
                .email("user@example.com")
                .username("사용자")
                .loginId("user")
                .password("password")
                .nickName("사용자")
                .role(Role.USER)
                .build();
    }
}
