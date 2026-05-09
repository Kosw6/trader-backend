package com.example.trader.ws.raw.lock;

import com.example.trader.repository.CanvasLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * DB 기반 락의 만료 레코드 정리 스케줄러.
 *
 * Redis 정상 구간에서는 Redis TTL이 자동으로 만료를 처리하므로
 * DB 락 테이블에 레코드가 쌓이지 않는다.
 * Redis 장애 구간에서만 DB 락이 사용되므로 해당 구간의 만료 레코드를 주기적으로 정리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanvasLockCleanupScheduler {

    private final CanvasLockRepository canvasLockRepository;

    @Scheduled(fixedDelayString = "${canvas.lock.cleanup-delay-ms:30000}")
    public void cleanupExpired() {
        int deleted = canvasLockRepository.deleteAllExpired(LocalDateTime.now());
        if (deleted > 0) {
            log.info("[CanvasLock] 만료된 DB 락 {}건 정리 완료", deleted);
        }
    }
}
