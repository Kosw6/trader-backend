package com.example.trader.ws.raw.edit;

import com.example.trader.entity.EditSessionEntity;
import com.example.trader.repository.EditSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpiredEditSessionCleanupService {

    private final EditSessionRepository editSessionRepository;

    /**
     * 오래된 세션을 한 번에 최대 100건만 제거해 정리 작업이 DB를 오래 점유하지 않게 한다.
     */
    @Transactional
    public int deleteExpiredBefore(LocalDateTime cutoff) {
        List<EditSessionEntity> expired =
                editSessionRepository.findTop100ByUpdatedAtBeforeOrderByUpdatedAtAsc(cutoff);
        if (expired.isEmpty()) {
            return 0;
        }

        // Entity remove 경로를 사용해 ElementCollection인 dirtyFields도 함께 정리한다.
        editSessionRepository.deleteAll(expired);
        return expired.size();
    }
}
