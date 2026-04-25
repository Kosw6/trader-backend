package com.example.trader.repository;

import com.example.trader.entity.NodeHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NodeHistoryRepository extends JpaRepository<NodeHistory, Long> {

    /**
     * Redis 힌트 체인 불완전 시 DB fallback.
     * baseVersion 이후 변경된 모든 이력 조회 (버전 오름차순).
     */
    List<NodeHistory> findByNodeIdAndVersionGreaterThanOrderByVersionAsc(Long nodeId, Integer baseVersion);

    /**
     * 특정 버전 범위 내 이력 조회.
     */
    List<NodeHistory> findByNodeIdAndVersionBetweenOrderByVersionAsc(Long nodeId, Integer fromVersion, Integer toVersion);
}
