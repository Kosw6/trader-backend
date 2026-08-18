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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
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
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, String> localSessions = new ConcurrentHashMap<>();

    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final Duration HINT_TTL = Duration.ofHours(1);

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

    public void startEditSession(Long teamId, Long graphId, Long nodeId, Long userId,
                                 int baseVersion, List<String> fields) {

        String key = editSessionKey(teamId, graphId, nodeId, userId);

        try {
            EditSessionDto existing = getEditSession(teamId, graphId, nodeId, userId)
                    .orElse(null);
            boolean preserveDraft = existing != null && existing.draftData() != null;

            String json = objectMapper.writeValueAsString(
                    new EditSessionDto(
                            preserveDraft ? existing.baseVersion() : baseVersion,
                            preserveDraft ? existing.fields() : fields,
                            preserveDraft ? existing.draftData() : null
                    )
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
            log.warn("[EditSession-START-LOCAL] saved. key={}, reason=redis_unavailable", key);
            return;
        }

        if (fallbackOff()) {
            log.debug("[EditSession] fallback disabled. session skipped. key={}", key);
            return;
        }

        if (dbFallbackEnabled()) {
            log.info("[EditSession] db fallback mode. start session is not persisted. key={}", key);
        }
    }

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

        localSessions.remove(key);

        if (!redisHealthState.isAvailable()) {
            removeSessionFallback(teamId, graphId, nodeId, userId, key, "redis_unavailable");
            return;
        }

        try {
            redis.delete(key);
            redisHealthState.markUp();

            if (dbFallbackEnabled()) {
                editSessionRepository.deleteById(new EditSessionId(teamId, graphId, nodeId, userId));
            }
        } catch (Exception e) {
            redisHealthState.markDown();
            removeSessionFallback(teamId, graphId, nodeId, userId, key, "redis_delete_failed");
            log.warn("[EditSession] Redis delete failed. fallback removed. key={}, reason={}", key, e.getMessage());
        }
    }

    private void removeSessionFallback(Long teamId, Long graphId, Long nodeId, Long userId,
                                       String key, String reason) {
        if (dbFallbackEnabled()) {
            editSessionRepository.deleteById(new EditSessionId(teamId, graphId, nodeId, userId));
            log.warn("[EditSession-END-DB] deleted. key={}, reason={}", key, reason);
        } else {
            localSessions.remove(key);
            log.warn("[EditSession-END-LOCAL] removed. key={}, reason={}", key, reason);
        }
    }

    public Optional<EditSessionDto> getEditSession(Long teamId, Long graphId, Long nodeId, Long userId) {

        String key = editSessionKey(teamId, graphId, nodeId, userId);

        try {
            String json = readSessionJson(key);

            if (json != null) {
                return Optional.of(objectMapper.readValue(json, EditSessionDto.class));
            }

            return readSessionFromDb(teamId, graphId, nodeId, userId);

        } catch (Exception e) {
            log.warn("[EditSession] getEditSession failed. key={}, reason={}", key, e.getMessage());
            return readSessionFromDb(teamId, graphId, nodeId, userId);
        }
    }

    private Optional<EditSessionDto> readSessionFromDb(Long teamId, Long graphId,
                                                       Long nodeId, Long userId) {
        if (!dbFallbackEnabled()) {
            return Optional.empty();
        }

        try {
            return editSessionRepository
                    .findWithDirtyFieldsByTeamIdAndGraphIdAndNodeIdAndUserId(
                            teamId, graphId, nodeId, userId)
                    .map(entity -> {
                        Object draftData = null;
                        if (entity.getDraftData() != null && !entity.getDraftData().isBlank()) {
                            try {
                                draftData = objectMapper.readValue(entity.getDraftData(), Object.class);
                            } catch (Exception e) {
                                throw new IllegalStateException("Failed to deserialize DB draft", e);
                            }
                        }

                        return new EditSessionDto(
                                entity.getBaseVersion() != null ? entity.getBaseVersion() : 0,
                                entity.getDirtyFields() != null ? entity.getDirtyFields() : List.of(),
                                draftData
                        );
                    });
        } catch (Exception e) {
            log.warn("[EditSession-READ-DB-FAILED] nodeId={}, userId={}, reason={}",
                    nodeId, userId, e.getMessage());
            return Optional.empty();
        }
    }

    private String readSessionJson(String key) {
        if (!redisHealthState.isAvailable()) {
            String localJson = localFallbackEnabled() ? localSessions.get(key) : null;

            log.debug("[EditSession-READ-LOCAL] key={}, hit={}",
                    key, localJson != null);

            return localJson;
        }

        try {
            String json = redis.opsForValue().get(key);
            redisHealthState.markUp();
            return json;
        } catch (Exception e) {
            redisHealthState.markDown();
            log.warn("[EditSession] Redis read failed. fallbackMode={}, key={}, reason={}",
                    fallbackMode, key, e.getMessage());

            String localJson = localFallbackEnabled() ? localSessions.get(key) : null;

            log.debug("[EditSession-READ-LOCAL] key={}, hit={}, reason=redis_read_failed",
                    key, localJson != null);

            return localJson;
        }
    }

    public boolean saveDraft(Long teamId, Long graphId, Long nodeId, Long userId,
                             int baseVersion, Object draftData, List<String> dirtyFields) {

        String key = editSessionKey(teamId, graphId, nodeId, userId);

        log.info("[EditSession-AUTOSAVE] start. fallbackMode={}, redisAvailable={}, nodeId={}, userId={}, dirtyFields={}",
                fallbackMode, redisHealthState.isAvailable(), nodeId, userId, dirtyFields);

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

                // Redis가 읽기 성공 후 쓰기 시점에 다운된 경우 → db fallback으로 즉시 전환
                if (!redisHealthState.isAvailable() && dbFallbackEnabled()) {
                    return saveDraftToDb(teamId, graphId, nodeId, userId,
                            baseVersion, draftData, dirtyFields);
                }

                return writeUpdatedDraft(key, updatedJson, nodeId, userId, dirtyFields);
            }

            if (localFallbackEnabled()) {
                EditSessionDto created = new EditSessionDto(
                        baseVersion,
                        dirtyFields != null ? dirtyFields : List.of(),
                        draftData
                );

                localSessions.put(key, objectMapper.writeValueAsString(created));

                log.warn("[EditSession-AUTOSAVE-LOCAL] saved. key={}, nodeId={}, userId={}, reason=session_missing_or_redis_unavailable",
                        key, nodeId, userId);

                return true;
            }

            if (dbFallbackEnabled()) {
                return saveDraftToDb(teamId, graphId, nodeId, userId,
                        baseVersion, draftData, dirtyFields);
            }

            log.warn("[EditSession-AUTOSAVE-SKIPPED] fallbackMode=off, key={}, nodeId={}, userId={}",
                    key, nodeId, userId);

            return false;

        } catch (Exception e) {
            log.warn("[EditSession-AUTOSAVE-FAILED] fallbackMode={}, nodeId={}, userId={}, reason={}",
                    fallbackMode, nodeId, userId, e.getMessage());
            return false;
        }
    }

    private boolean writeUpdatedDraft(String key, String updatedJson,
                                      Long nodeId, Long userId, List<String> dirtyFields) {
        if (!redisHealthState.isAvailable()) {
            if (localFallbackEnabled()) {
                localSessions.put(key, updatedJson);

                log.warn("[EditSession-AUTOSAVE-LOCAL] updated. key={}, nodeId={}, userId={}, dirtyFields={}, reason=redis_unavailable",
                        key, nodeId, userId, dirtyFields);

                return true;
            }

            log.warn("[EditSession-AUTOSAVE-FAILED] redisUnavailable=true, fallbackMode={}, key={}, nodeId={}, userId={}",
                    fallbackMode, key, nodeId, userId);

            return false;
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            redis.opsForValue().set(key, updatedJson, SESSION_TTL);
            redisHealthState.markUp();
            sample.stop(Timer.builder("edit.session.autosave")
                    .tag("path", "redis")
                    .register(meterRegistry));

            log.info("[EditSession-AUTOSAVE-REDIS] saved. key={}, nodeId={}, userId={}, dirtyFields={}",
                    key, nodeId, userId, dirtyFields);

            return true;
        } catch (Exception e) {
            redisHealthState.markDown();

            if (localFallbackEnabled()) {
                localSessions.put(key, updatedJson);

                log.warn("[EditSession-AUTOSAVE-LOCAL] updated. key={}, nodeId={}, userId={}, dirtyFields={}, reason=redis_write_failed",
                        key, nodeId, userId, dirtyFields);

                return true;
            }

            log.warn("[EditSession-AUTOSAVE] Redis write failed mid-request. fallbackMode={}, key={}, nodeId={}, userId={}, reason={}",
                    fallbackMode, key, nodeId, userId, e.getMessage());

            // saveDraft()에서 사전 분기하므로 여기까지는 db 모드가 도달하지 않음.
            // 방어적으로 처리.
            return false;
        }
    }

    private boolean saveDraftToDb(Long teamId, Long graphId, Long nodeId, Long userId,
                                  int baseVersion, Object draftData, List<String> dirtyFields) {
        try {
            EditSessionId id = new EditSessionId(teamId, graphId, nodeId, userId);

            // entity가 없으면 새로 생성 (EDIT_START가 Redis에만 저장됐다가 장애로 유실된 경우 대비)
            EditSessionEntity entity = editSessionRepository.findById(id)
                    .orElseGet(() -> {
                        log.warn("[EditSession-AUTOSAVE-DB] session_not_found → upsert. nodeId={}, userId={}", nodeId, userId);
                        return EditSessionEntity.builder()
                                .teamId(teamId)
                                .graphId(graphId)
                                .nodeId(nodeId)
                                .userId(userId)
                                .baseVersion(baseVersion)
                                .build();
                    });

            String json = objectMapper.writeValueAsString(draftData);
            entity.updateDraft(json, dirtyFields);

            Timer.Sample sample = Timer.start(meterRegistry);
            editSessionRepository.save(entity);
            sample.stop(Timer.builder("edit.session.autosave")
                    .tag("path", "db-fallback")
                    .register(meterRegistry));

            log.warn("[EditSession-AUTOSAVE-DB] saved. nodeId={}, userId={}, dirtyFields={}",
                    nodeId, userId, dirtyFields);

            return true;

        } catch (Exception e) {
            log.warn("[EditSession-AUTOSAVE-DB-FAILED] nodeId={}, userId={}, reason={}",
                    nodeId, userId, e.getMessage());
            return false;
        }
    }

    public void saveVersionHint(Long teamId, Long graphId, Long nodeId,
                                int version, List<String> changedFields, Long changedBy) {

        if (!redisHealthState.isAvailable()) {
            log.info("[EditSession-VERSION-HINT-SKIPPED] reason=redis_unavailable, nodeId={}, version={}",
                    nodeId, version);
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

        // 버전 범위의 키를 한 번에 수집
        List<Integer> versions = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (int version = fromVersion + 1; version <= toVersion; version++) {
            versions.add(version);
            keys.add(versionHintKey(teamId, graphId, nodeId, version));
        }

        if (keys.isEmpty()) {
            return new LinkedHashMap<>();
        }

        try {
            // N번 순차 GET → 1번 MGET (단일 round-trip)
            List<String> jsons = redis.opsForValue().multiGet(keys);

            if (jsons == null) {
                return null;
            }

            Map<Integer, List<String>> hints = new LinkedHashMap<>();
            for (int i = 0; i < versions.size(); i++) {
                String json = jsons.get(i);
                if (json == null) {
                    // 힌트 체인 끊김 → DB fallback으로 전환
                    return null;
                }
                VersionHintDto dto = objectMapper.readValue(json, VersionHintDto.class);
                hints.put(versions.get(i), dto.changedFields());
            }

            redisHealthState.markUp();
            return hints;

        } catch (Exception e) {
            redisHealthState.markDown();
            log.warn("[EditSession] getVersionHints failed. nodeId={}, fromVersion={}, toVersion={}, reason={}",
                    nodeId, fromVersion, toVersion, e.getMessage());
            return null;
        }
    }
    //기존 하나씩 가져옴
//    public Map<Integer, List<String>> getVersionHints(Long teamId, Long graphId, Long nodeId,
//                                                      int fromVersion, int toVersion) {
//
//        if (!redisHealthState.isAvailable()) {
//            return null;
//        }
//
//        Map<Integer, List<String>> hints = new LinkedHashMap<>();
//
//        for (int version = fromVersion + 1; version <= toVersion; version++) {
//            try {
//                String key = versionHintKey(teamId, graphId, nodeId, version);
//                String json = redis.opsForValue().get(key);
//
//                if (json == null) {
//                    return null;
//                }
//
//                VersionHintDto dto = objectMapper.readValue(json, VersionHintDto.class);
//                hints.put(version, dto.changedFields());
//
//            } catch (Exception e) {
//                redisHealthState.markDown();
//                log.warn("[EditSession] getVersionHints failed. nodeId={}, fromVersion={}, toVersion={}, reason={}",
//                        nodeId, fromVersion, toVersion, e.getMessage());
//                return null;
//            }
//        }
//
//        redisHealthState.markUp();
//        return hints;
//    }

    public boolean hasCompleteChain(Long teamId, Long graphId, Long nodeId,
                                    int fromVersion, int toVersion) {
        return getVersionHints(teamId, graphId, nodeId, fromVersion, toVersion) != null;
    }

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
