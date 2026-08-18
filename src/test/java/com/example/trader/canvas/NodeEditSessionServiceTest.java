package com.example.trader.canvas;

import com.example.trader.dto.canvas.EditSessionDto;
import com.example.trader.dto.canvas.VersionHintDto;
import com.example.trader.entity.EditSessionEntity;
import com.example.trader.entity.EditSessionId;
import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.realtime.RealtimePublisher;
import com.example.trader.repository.EditSessionRepository;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NodeEditSessionService 단위 테스트.
 *
 * 검증 목표:
 *  - 편집 세션 저장/삭제/조회의 Redis 키 포맷 및 TTL
 *  - 버전 힌트 체인 완전/불완전 분기 (핵심: null 반환 여부)
 *  - 직렬화 오류 시 안전하게 empty/null 처리
 */
@ExtendWith(MockitoExtension.class)
class NodeEditSessionServiceTest {

    @Mock StringRedisTemplate             stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock
    EditSessionRepository repository;
    @Mock
    RealtimePublisher publisher;
    @Mock
    RedisHealthState state;
    final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    // 실제 ObjectMapper — 직렬화/역직렬화 실제 동작 검증
    final ObjectMapper objectMapper = new ObjectMapper();

    NodeEditSessionService service;

    static final Long TEAM_ID  = 1L;
    static final Long GRAPH_ID = 2L;
    static final Long NODE_ID  = 3L;
    static final Long USER_ID  = 10L;

    static final Duration SESSION_TTL = Duration.ofMinutes(10);
    static final Duration HINT_TTL    = Duration.ofHours(1);

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(state.isAvailable()).thenReturn(true);
        service = new NodeEditSessionService(stringRedisTemplate, objectMapper,repository, publisher,state,meterRegistry);
        ReflectionTestUtils.setField(service, "fallbackMode", "local");
    }

    // ── 편집 세션 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("startEditSession → 올바른 키에 JSON 저장, TTL 10분")
    void start_edit_session_saves_to_redis() {
        List<String> fields = List.of("subject", "content");

        service.startEditSession(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 3, fields);

        // set() 호출 확인
        ArgumentCaptor<String> keyCaptor   = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        // 키 포맷 확인
        assertThat(keyCaptor.getValue())
                .isEqualTo("canvas:editing:1:2:3:10");
        // TTL 10분
        assertThat(ttlCaptor.getValue()).isEqualTo(SESSION_TTL);
        // JSON에 baseVersion, fields 포함 확인
        assertThat(valueCaptor.getValue()).contains("\"baseVersion\":3");
        assertThat(valueCaptor.getValue()).contains("subject");
    }

    @Test
    @DisplayName("endEditSession → 올바른 키 삭제")
    void end_edit_session_deletes_key() {
        service.endEditSession(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);

        verify(stringRedisTemplate).delete("canvas:editing:1:2:3:10");
    }

    @Test
    @DisplayName("getEditSession → Redis에 값 있으면 Optional<EditSessionDto> 반환")
    void get_edit_session_returns_dto_when_present() throws Exception {
        EditSessionDto dto = new EditSessionDto(3, List.of("subject"));
        String json = objectMapper.writeValueAsString(dto);
        when(valueOps.get("canvas:editing:1:2:3:10")).thenReturn(json);

        Optional<EditSessionDto> result = service.getEditSession(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);

        assertThat(result).isPresent();
        assertThat(result.get().baseVersion()).isEqualTo(3);
        assertThat(result.get().fields()).containsExactly("subject");
    }

    @Test
    @DisplayName("getEditSession → Redis에 값 없으면 empty 반환")
    void get_edit_session_returns_empty_when_absent() {
        when(valueOps.get(anyString())).thenReturn(null);

        Optional<EditSessionDto> result = service.getEditSession(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);

        assertThat(result).isEmpty();
    }

    // ── 버전 힌트 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("saveVersionHint → 올바른 키에 저장, TTL 1시간")
    void save_version_hint_uses_correct_key_and_ttl() {
        service.saveVersionHint(TEAM_ID, GRAPH_ID, NODE_ID, 3, List.of("subject"), USER_ID);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

        verify(valueOps).set(keyCaptor.capture(), anyString(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo("canvas:version-hint:1:2:3:3");
        assertThat(ttlCaptor.getValue()).isEqualTo(HINT_TTL);
    }

    @Test
    @DisplayName("getVersionHints → base=2, current=4: v3/v4 모두 존재 → 완전한 Map 반환")
    void get_version_hints_returns_complete_map() throws Exception {
        VersionHintDto hint3 = new VersionHintDto(3, List.of("content"), 99L, System.currentTimeMillis());
        VersionHintDto hint4 = new VersionHintDto(4, List.of("symb"), 88L, System.currentTimeMillis());

        when(valueOps.multiGet(List.of(
                "canvas:version-hint:1:2:3:3",
                "canvas:version-hint:1:2:3:4"
        ))).thenReturn(List.of(
                objectMapper.writeValueAsString(hint3),
                objectMapper.writeValueAsString(hint4)
        ));

        Map<Integer, List<String>> result = service.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 4);

        assertThat(result).isNotNull();
        assertThat(result.get(3)).containsExactly("content");
        assertThat(result.get(4)).containsExactly("symb");
        verify(valueOps).multiGet(List.of(
                "canvas:version-hint:1:2:3:3",
                "canvas:version-hint:1:2:3:4"
        ));
    }

    @Test
    @DisplayName("getVersionHints → 중간 버전(v3) 누락 → null 반환 (DB fallback 신호)")
    void get_version_hints_returns_null_when_chain_incomplete() {
        when(valueOps.multiGet(List.of(
                "canvas:version-hint:1:2:3:3",
                "canvas:version-hint:1:2:3:4"
        ))).thenReturn(Arrays.asList(null, "unused"));

        Map<Integer, List<String>> result = service.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 4);

        // 체인 불완전 → null
        assertThat(result).isNull();
        verify(valueOps).multiGet(List.of(
                "canvas:version-hint:1:2:3:3",
                "canvas:version-hint:1:2:3:4"
        ));
    }

    @Test
    @DisplayName("getVersionHints → from == to (버전 변경 없음) → 빈 Map 반환, Redis 조회 없음")
    void get_version_hints_returns_empty_map_when_no_range() {
        Map<Integer, List<String>> result = service.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 3, 3);

        assertThat(result).isNotNull().isEmpty();
        verifyNoInteractions(valueOps);
    }

    @Test
    @DisplayName("hasCompleteChain → 모든 힌트 존재 시 true")
    void has_complete_chain_returns_true_when_all_hints_present() throws Exception {
        VersionHintDto hint = new VersionHintDto(3, List.of("subject"), USER_ID, System.currentTimeMillis());
        when(valueOps.multiGet(List.of("canvas:version-hint:1:2:3:3")))
                .thenReturn(List.of(objectMapper.writeValueAsString(hint)));

        boolean complete = service.hasCompleteChain(TEAM_ID, GRAPH_ID, NODE_ID, 2, 3);

        assertThat(complete).isTrue();
    }

    @Test
    @DisplayName("hasCompleteChain → 힌트 누락 시 false")
    void has_complete_chain_returns_false_when_hint_missing() {
        when(valueOps.multiGet(List.of("canvas:version-hint:1:2:3:3")))
                .thenReturn(Arrays.asList((String) null));

        boolean complete = service.hasCompleteChain(TEAM_ID, GRAPH_ID, NODE_ID, 2, 3);

        assertThat(complete).isFalse();
    }

    @Test
    @DisplayName("startEditSession → 기존 Draft와 baseVersion을 덮어쓰지 않음")
    void start_edit_session_preserves_existing_draft() throws Exception {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("fields", Map.of("subject", "임시 제목"));
        EditSessionDto existing = new EditSessionDto(4, List.of("subject"), draft);
        when(valueOps.get("canvas:editing:1:2:3:10"))
                .thenReturn(objectMapper.writeValueAsString(existing));

        service.startEditSession(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 7, List.of());

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq("canvas:editing:1:2:3:10"), jsonCaptor.capture(), eq(SESSION_TTL));
        EditSessionDto saved = objectMapper.readValue(jsonCaptor.getValue(), EditSessionDto.class);
        assertThat(saved.baseVersion()).isEqualTo(4);
        assertThat(saved.fields()).containsExactly("subject");
        assertThat(saved.draftData()).isEqualTo(draft);
    }

    @Test
    @DisplayName("getEditSession → Redis 장애 시 DB Draft 복원")
    void get_edit_session_restores_db_fallback_draft() {
        when(state.isAvailable()).thenReturn(false);
        ReflectionTestUtils.setField(service, "fallbackMode", "db");

        EditSessionEntity entity = EditSessionEntity.builder()
                .teamId(TEAM_ID).graphId(GRAPH_ID).nodeId(NODE_ID).userId(USER_ID)
                .baseVersion(5)
                .draftData("{\"fields\":{\"subject\":\"복원 제목\"}}")
                .dirtyFields(List.of("subject"))
                .build();
        when(repository.findWithDirtyFieldsByTeamIdAndGraphIdAndNodeIdAndUserId(
                TEAM_ID, GRAPH_ID, NODE_ID, USER_ID)).thenReturn(Optional.of(entity));

        Optional<EditSessionDto> restored = service.getEditSession(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);

        assertThat(restored).isPresent();
        assertThat(restored.orElseThrow().baseVersion()).isEqualTo(5);
        assertThat(restored.orElseThrow().fields()).containsExactly("subject");
        assertThat(restored.orElseThrow().draftData()).isEqualTo(
                Map.of("fields", Map.of("subject", "복원 제목")));
    }

    @Test
    @DisplayName("saveDraft → DB fallback 신규 row에 전달받은 baseVersion 저장")
    void save_draft_uses_requested_base_version_for_db_fallback() {
        when(state.isAvailable()).thenReturn(false);
        ReflectionTestUtils.setField(service, "fallbackMode", "db");
        when(repository.findById(new EditSessionId(TEAM_ID, GRAPH_ID, NODE_ID, USER_ID)))
                .thenReturn(Optional.empty());

        boolean saved = service.saveDraft(
                TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 6,
                Map.of("fields", Map.of("subject", "임시 제목")),
                List.of("subject")
        );

        assertThat(saved).isTrue();
        ArgumentCaptor<EditSessionEntity> entityCaptor = ArgumentCaptor.forClass(EditSessionEntity.class);
        verify(repository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getBaseVersion()).isEqualTo(6);
        assertThat(entityCaptor.getValue().getDirtyFields()).containsExactly("subject");
    }

    @Test
    @DisplayName("saveDraft → local fallback 신규 세션에도 전달받은 baseVersion 저장")
    void save_draft_uses_requested_base_version_for_local_fallback() {
        when(state.isAvailable()).thenReturn(false);

        boolean saved = service.saveDraft(
                TEAM_ID, GRAPH_ID, NODE_ID, USER_ID, 8,
                Map.of("fields", Map.of("subject", "임시 제목")),
                List.of("subject")
        );

        assertThat(saved).isTrue();
        Optional<EditSessionDto> restored = service.getEditSession(
                TEAM_ID, GRAPH_ID, NODE_ID, USER_ID);
        assertThat(restored).isPresent();
        assertThat(restored.orElseThrow().baseVersion()).isEqualTo(8);
    }
}
