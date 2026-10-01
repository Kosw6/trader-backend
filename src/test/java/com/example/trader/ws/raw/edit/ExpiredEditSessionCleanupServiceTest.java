package com.example.trader.ws.raw.edit;

import com.example.trader.entity.EditSessionEntity;
import com.example.trader.repository.EditSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredEditSessionCleanupServiceTest {

    @Mock EditSessionRepository editSessionRepository;

    private ExpiredEditSessionCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new ExpiredEditSessionCleanupService(editSessionRepository);
    }

    @Test
    @DisplayName("기준 시각보다 오래된 DB 편집 세션을 조회해 삭제")
    void deletes_expired_sessions() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 12, 12, 0);
        List<EditSessionEntity> expired = List.of(
                EditSessionEntity.builder().teamId(1L).graphId(2L).nodeId(3L).userId(4L).build(),
                EditSessionEntity.builder().teamId(1L).graphId(2L).nodeId(5L).userId(4L).build()
        );
        when(editSessionRepository.findTop100ByUpdatedAtBeforeOrderByUpdatedAtAsc(cutoff))
                .thenReturn(expired);

        int deleted = cleanupService.deleteExpiredBefore(cutoff);

        assertThat(deleted).isEqualTo(2);
        verify(editSessionRepository).deleteAll(expired);
    }

    @Test
    @DisplayName("만료된 DB 편집 세션이 없으면 삭제를 호출하지 않음")
    void skips_delete_when_nothing_expired() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 12, 12, 0);
        when(editSessionRepository.findTop100ByUpdatedAtBeforeOrderByUpdatedAtAsc(cutoff))
                .thenReturn(List.of());

        assertThat(cleanupService.deleteExpiredBefore(cutoff)).isZero();

        verify(editSessionRepository, never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
    }
}
