package com.example.trader.service.event;

import com.example.trader.service.cache.CacheInvalidationRetryService;
import com.example.trader.service.cache.NodeQueryCacheInvalidator;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 확정된 팀 Node 변경에서 파생되는 best-effort 후처리를 수행한다.
 * 각 작업은 서로 독립적이며 실패해도 이미 커밋된 업무 결과를 되돌리지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamNodeChangeAfterCommitListener {

    private final NodeEditSessionService editSessionService;
    private final NodeQueryCacheInvalidator cacheInvalidator;
    private final CacheInvalidationRetryService cacheInvalidationRetryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TeamNodeChangeCommittedEvent event) {
        runIndependently("version_hint", event, () -> editSessionService.saveVersionHint(
                event.teamId(),
                event.graphId(),
                event.nodeId(),
                event.version(),
                event.changedFields(),
                event.userId()
        ));

        // endEditSession은 세션 제거와 TRANSIENT EDIT_END 1회 전파를 함께 수행한다.
        runIndependently("edit_session_end", event, () -> editSessionService.endEditSession(
                event.teamId(),
                event.graphId(),
                event.nodeId(),
                event.userId()
        ));

        invalidateCaches(event);

        log.info(
                "node update committed teamId={} graphId={} nodeId={} userId={} newVersion={} changedFields={}",
                event.teamId(),
                event.graphId(),
                event.nodeId(),
                event.userId(),
                event.version(),
                event.changedFields()
        );
    }

    private void invalidateCaches(TeamNodeChangeCommittedEvent event) {
        try {
            cacheInvalidator.invalidate(event.graphId());
        } catch (Exception invalidationFailure) {
            log.warn(
                    "node update cache invalidation failed graphId={} nodeId={} version={} reason={}",
                    event.graphId(),
                    event.nodeId(),
                    event.version(),
                    invalidationFailure.getMessage(),
                    invalidationFailure
            );
            try {
                cacheInvalidationRetryService.recordInitialFailure(
                        event.graphId(),
                        invalidationFailure
                );
            } catch (Exception recordFailure) {
                // DB 복구 작업 기록까지 실패하면 TTL 15분이 최종 수렴 경계가 된다.
                log.error(
                        "cache invalidation retry task record failed graphId={} nodeId={} reason={}",
                        event.graphId(),
                        event.nodeId(),
                        recordFailure.getMessage(),
                        recordFailure
                );
            }
        }
    }

    private void runIndependently(
            String operation,
            TeamNodeChangeCommittedEvent event,
            Runnable action
    ) {
        try {
            action.run();
        } catch (Exception exception) {
            log.warn(
                    "node update after-commit task failed operation={} teamId={} graphId={} nodeId={} version={} reason={}",
                    operation,
                    event.teamId(),
                    event.graphId(),
                    event.nodeId(),
                    event.version(),
                    exception.getMessage(),
                    exception
            );
        }
    }
}
