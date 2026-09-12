package com.example.trader.ws.raw.edit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class EditSessionCleanupScheduler {

    private final ExpiredEditSessionCleanupService cleanupService;

    @Scheduled(fixedDelayString = "${edit-session.cleanup-delay-ms:60000}")
    public void cleanupExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minus(NodeEditSessionService.SESSION_TTL);
        int deleted = cleanupService.deleteExpiredBefore(cutoff);
        if (deleted > 0) {
            log.info("[EditSession] 10분 이상 갱신되지 않은 DB 세션 {}건 정리 완료", deleted);
        }
    }
}
