package com.example.trader.canvas;

import com.example.trader.dto.canvas.EditSessionDto;
import com.example.trader.dto.canvas.VersionHintDto;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
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
        service = new NodeEditSessionService(stringRedisTemplate, objectMapper);
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

        when(valueOps.get("canvas:version-hint:1:2:3:3"))
                .thenReturn(objectMapper.writeValueAsString(hint3));
        when(valueOps.get("canvas:version-hint:1:2:3:4"))
                .thenReturn(objectMapper.writeValueAsString(hint4));

        Map<Integer, List<String>> result = service.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 4);

        assertThat(result).isNotNull();
        assertThat(result.get(3)).containsExactly("content");
        assertThat(result.get(4)).containsExactly("symb");
        // Redis 조회 2번 발생 (v3, v4)
        verify(valueOps, times(2)).get(anyString());
    }

    @Test
    @DisplayName("getVersionHints → 중간 버전(v3) 누락 → null 반환 (DB fallback 신호)")
    void get_version_hints_returns_null_when_chain_incomplete() {
        // v3 없음(null) → 단락 평가로 즉시 null 반환 → v4 조회 자체가 발생하지 않음
        when(valueOps.get("canvas:version-hint:1:2:3:3")).thenReturn(null);

        Map<Integer, List<String>> result = service.getVersionHints(TEAM_ID, GRAPH_ID, NODE_ID, 2, 4);

        // 체인 불완전 → null
        assertThat(result).isNull();
        // v3 누락 확인 즉시 반환 → v4 조회 없어야 함 (단락 평가)
        verify(valueOps, times(1)).get("canvas:version-hint:1:2:3:3");
        verify(valueOps, never()).get("canvas:version-hint:1:2:3:4");
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
        when(valueOps.get("canvas:version-hint:1:2:3:3"))
                .thenReturn(objectMapper.writeValueAsString(hint));

        boolean complete = service.hasCompleteChain(TEAM_ID, GRAPH_ID, NODE_ID, 2, 3);

        assertThat(complete).isTrue();
    }

    @Test
    @DisplayName("hasCompleteChain → 힌트 누락 시 false")
    void has_complete_chain_returns_false_when_hint_missing() {
        when(valueOps.get("canvas:version-hint:1:2:3:3")).thenReturn(null);

        boolean complete = service.hasCompleteChain(TEAM_ID, GRAPH_ID, NODE_ID, 2, 3);

        assertThat(complete).isFalse();
    }
}
