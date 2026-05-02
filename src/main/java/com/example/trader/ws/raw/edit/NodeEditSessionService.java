package com.example.trader.ws.raw.edit;

import com.example.trader.dto.canvas.EditSessionDto;
import com.example.trader.dto.canvas.VersionHintDto;
import com.example.trader.entity.EditSessionEntity;
import com.example.trader.entity.EditSessionId;
import com.example.trader.infra.redis.RedisHealthState;
import com.example.trader.realtime.RealtimePublisher;
import com.example.trader.realtime.message.RealtimeEnvelope;
import com.example.trader.realtime.message.RealtimeSubType;
import com.example.trader.realtime.message.RealtimeType;
import com.example.trader.repository.EditSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeEditSessionService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final EditSessionRepository editSessionRepository;
    private final RealtimePublisher realtimePublisher;
    private final RedisHealthState redisHealthState;

    /**
     * Redis 장애 시 사용하는 로컬 인메모리 저장소.
     *
     * 특징:
     * - 빠름
     * - 현재 app instance 안에서만 유효
     * - app 재시작 시 유실
     * - 멀티 인스턴스 간 공유 불가
     */
    private final ConcurrentHashMap<String, String> localSessions = new ConcurrentHashMap<>();

    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final Duration HINT_TTL = Duration.ofHours(1);

    /**
     * local | db | off
     *
     * 기본값 local.
     *
     * application.yml:
     *
     * edit-session:
     *   fallback-mode: ${EDIT_SESSION_FALLBACK_MODE:local}
     */
    @Value("${edit-session.fallback-mode:local}")
    private String fallbackMode;

    private String editSessionKey(Long teamId, Long graphId, Long nodeId, Long userId) {
        return "canvas:editing:" + teamId + ":" + graphId + ":" + nodeId + ":" + userId;
    }

    private String versionHintKey(Long teamId, Long graphId, Long nodeId, int version) {
        return "canvas:version-hint:" + teamId + ":" + graphId + ":" + nodeId + ":" + version;
    }

    private boolean localFallbackEnabled() {
        return "local".equalsIgnoreCase(fallbackMode);
    }

    private boolean dbFallbackEnabled() {
        return "db".equalsIgnoreCase(fallbackMode);
    }

    private boolean fallbackOff() {
        return "off".equalsIgnoreCase(fallbackMode);
    }

    // ─────────────────────────────────────────────
    // EDIT START
    // ─────────────────────────────────────────────

    public void startEditSession(Long teamId, Long graphId, Long nodeId, Long userId,
                                 int baseVersion, List<String> fields) {

        String key = editSessionKey(teamId, graphId, nodeId, userId);

        try {
            String json = objectMapper.writeValueAsString(
                    new EditSessionDto(baseVersion, fields)
            );

            if (!redisHealthState.isAvailable()) {
                saveSessionFallback(key, json);
            } else {
                try {
                    redis.opsForValue().set(key, json, SESSION_TTL);
                    redisHealthState.markUp();
                } catch (Exception e) {
                    redisHealthState.markDown();
                    saveSessionFallback(key, json);
                    log.warn("[EditSession] Redis write failed. fallbackMode={}, key={}, reason={}",
                            fallbackMode, key, e.getMessage());
                }
            }

            publishEditEvent(
                    RealtimeSubType.EDIT_START,
                    teamId,
                    graphId,
                    nodeId,
                    userId,
                    baseVersion,
                    fields
            );

        } catch (Exception e) {
            log.warn("[EditSession] startEditSession failed. nodeId={}, userId={}, reason={}",
                    nodeId, userId, e.getMessage());
        }
    }

    private void saveSessionFallback(String key, String json) {
        if (localFallbackEnabled()) {
            localSessions.put(key, json);
            return;
        }

        if (fallbackOff()) {
            log.debug("[EditSession] fallback disabled. session skipped. key={}", key);
            return;
        }

        // db fallback은 startEditSession 단계에서는 저장하지 않음.
        // DB fallback은 autosave draft 저장 시점에서 수행한다.
        if (dbFallbackEnabled()) {
            log.debug("[EditSession] db fallback mode. start session is not persisted. key={}", key);
        }
    }

    // ─────────────────────────────────────────────
    // EDIT END / CANCEL
    // ─────────────────────────────────────────────

    public void endEditSession(Long teamId, Long graphId, Long nodeId, Long userId) {
        removeSession(teamId, graphId, nodeId, userId);
        publishEditEvent(
                RealtimeSubType.EDIT_END,
                teamId,
                graphId,
                nodeId,
                userId,
                null,
                null
        );
    }

    public void cancelEditSession(Long teamId, Long graphId, Long nodeId, Long userId) {
        removeSession(teamId, graphId, nodeId, userId);

        // 기존 enum 이름을 그대로 사용.
        // 가능하면 EDIT_CANCEL 로 오타 수정 권장.
        publishEditEvent(
                RealtimeSubType.EDIT_CANCEL,
                teamId,
                graphId,
                nodeId,
                userId,
                null,
                null
        );
    }

    private void removeSession(Long teamId, Long graphId, Long nodeId, Long userId) {
        String key = editSessionKey(teamId, graphId, nodeId, userId);

        if (!redisHealthState.isAvailable()) {
            localSessions.remove(key);
            return;
        }

        try {
            redis.delete(key);
            redisHealthState.markUp();
        } catch (Exception e) {
            redisHealthState.markDown();
            localSessions.remove(key);
            log.warn("[EditSession] Redis delete failed. local removed. key={}, reason={}",
                    key, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    // GET SESSION
    // ─────────────────────────────────────────────

    public Optional<EditSessionDto> getEditSession(Long teamId, Long graphId, Long nodeId, Long userId) {

        String key = editSessionKey(teamId, graphId, nodeId, userId);

        try {
            String json = readSessionJson(key);

            if (json == null) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(json, EditSessionDto.class));

        } catch (Exception e) {
            log.warn("[EditSession] getEditSession failed. key={}, reason={}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private String readSessionJson(String key) {
        if (!redisHealthState.isAvailable()) {
            return localFallbackEnabled() ? localSessions.get(key) : null;
        }

        try {
            String json = redis.opsForValue().get(key);
            redisHealthState.markUp();
            return json;
        } catch (Exception e) {
            redisHealthState.markDown();
            log.warn("[EditSession] Redis read failed. fallbackMode={}, key={}, reason={}",
                    fallbackMode, key, e.getMessage());

            return localFallbackEnabled() ? localSessions.get(key) : null;
        }
    }

    // ─────────────────────────────────────────────
    // AUTOSAVE
    // ─────────────────────────────────────────────

    public boolean saveDraft(Long teamId, Long graphId, Long nodeId, Long userId,
                             Object draftData, List<String> dirtyFields) {

        String key = editSessionKey(teamId, graphId, nodeId, userId);

        try {
            String currentJson = readSessionJson(key);

            if (currentJson != null) {
                EditSessionDto current = objectMapper.readValue(currentJson, EditSessionDto.class);

                EditSessionDto updated = new EditSessionDto(
                        current.baseVersion(),
                        dirtyFields != null ? dirtyFields : current.fields(),
                        draftData
                );

                String updatedJson = objectMapper.writeValueAsString(updated);
                return writeUpdatedDraft(key, updatedJson);
            }

            if (localFallbackEnabled()) {
                EditSessionDto created = new EditSessionDto(
                        0,
                        dirtyFields != null ? dirtyFields : List.of(),
                        draftData
                );

                localSessions.put(key, objectMapper.writeValueAsString(created));
                return true;
            }

            if (dbFallbackEnabled()) {
                return saveDraftToDb(teamId, graphId, nodeId, userId, draftData, dirtyFields);
            }

            return false;

        } catch (Exception e) {
            log.warn("[EditSession] saveDraft failed. fallbackMode={}, nodeId={}, userId={}, reason={}",
                    fallbackMode, nodeId, userId, e.getMessage());
            return false;
        }
    }

    private boolean writeUpdatedDraft(String key, String updatedJson) {
        if (!redisHealthState.isAvailable()) {
            if (localFallbackEnabled()) {
                localSessions.put(key, updatedJson);
                return true;
            }

            return false;
        }

        try {
            redis.opsForValue().set(key, updatedJson, SESSION_TTL);
            redisHealthState.markUp();
            return true;
        } catch (Exception e) {
            redisHealthState.markDown();

            if (localFallbackEnabled()) {
                localSessions.put(key, updatedJson);
                return true;
            }

            log.warn("[EditSession] Redis draft update failed. fallbackMode={}, key={}, reason={}",
                    fallbackMode, key, e.getMessage());
            return false;
        }
    }

    private boolean saveDraftToDb(Long teamId, Long graphId, Long nodeId, Long userId,
                                  Object draftData, List<String> dirtyFields) {
        try {
            EditSessionId id = new EditSessionId(teamId, graphId, nodeId, userId);

            Optional<EditSessionEntity> optional = editSessionRepository.findById(id);

            if (optional.isEmpty()) {
                return false;
            }

            EditSessionEntity entity = optional.get();

            String json = objectMapper.writeValueAsString(draftData);
            entity.updateDraft(json, dirtyFields);
            editSessionRepository.save(entity);

            return true;

        } catch (Exception e) {
            log.warn("[EditSession] DB fallback saveDraft failed. nodeId={}, userId={}, reason={}",
                    nodeId, userId, e.getMessage());
            return false;
        }
    }

    // ─────────────────────────────────────────────
    // VERSION HINT
    // ─────────────────────────────────────────────

    public void saveVersionHint(Long teamId, Long graphId, Long nodeId,
                                int version, List<String> changedFields, Long changedBy) {

        if (!redisHealthState.isAvailable()) {
            return;
        }

        try {
            String key = versionHintKey(teamId, graphId, nodeId, version);

            VersionHintDto dto = new VersionHintDto(
                    version,
                    changedFields,
                    changedBy,
                    System.currentTimeMillis()
            );

            redis.opsForValue().set(key, objectMapper.writeValueAsString(dto), HINT_TTL);
            redisHealthState.markUp();

        } catch (Exception e) {
            redisHealthState.markDown();
            log.warn("[EditSession] saveVersionHint failed. nodeId={}, version={}, reason={}",
                    nodeId, version, e.getMessage());
        }
    }

    public Map<Integer, List<String>> getVersionHints(Long teamId, Long graphId, Long nodeId,
                                                      int fromVersion, int toVersion) {

        if (!redisHealthState.isAvailable()) {
            return null;
        }

        Map<Integer, List<String>> hints = new LinkedHashMap<>();

        for (int version = fromVersion + 1; version <= toVersion; version++) {
            try {
                String key = versionHintKey(teamId, graphId, nodeId, version);
                String json = redis.opsForValue().get(key);

                if (json == null) {
                    return null;
                }

                VersionHintDto dto = objectMapper.readValue(json, VersionHintDto.class);
                hints.put(version, dto.changedFields());

            } catch (Exception e) {
                redisHealthState.markDown();
                log.warn("[EditSession] getVersionHints failed. nodeId={}, fromVersion={}, toVersion={}, reason={}",
                        nodeId, fromVersion, toVersion, e.getMessage());
                return null;
            }
        }

        redisHealthState.markUp();
        return hints;
    }

    public boolean hasCompleteChain(Long teamId, Long graphId, Long nodeId,
                                    int fromVersion, int toVersion) {
        return getVersionHints(teamId, graphId, nodeId, fromVersion, toVersion) != null;
    }

    // ─────────────────────────────────────────────
    // EVENT PUBLISH
    // ─────────────────────────────────────────────

    private void publishEditEvent(RealtimeSubType subType,
                                  Long teamId,
                                  Long graphId,
                                  Long nodeId,
                                  Long userId,
                                  Integer baseVersion,
                                  List<String> fields) {

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("nodeId", nodeId);
            payload.put("userId", userId);
            payload.put("timestamp", System.currentTimeMillis());

            if (baseVersion != null) {
                payload.put("baseVersion", baseVersion);
            }

            if (fields != null) {
                payload.put("fields", fields);
            }

            realtimePublisher.publish(
                    RealtimeEnvelope.builder()
                            .type(RealtimeType.RELIABLE)
                            .subType(subType)
                            .teamId(teamId)
                            .graphId(graphId)
                            .payload(payload)
                            .build()
            );

        } catch (Exception e) {
            log.warn("[EditSession] publish failed. subType={}, nodeId={}, userId={}, reason={}",
                    subType, nodeId, userId, e.getMessage());
        }
    }
}