package com.example.trader.repository;

import com.example.trader.entity.Edge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EdgeRepository extends JpaRepository<Edge,Long> {
    // EdgeRepository.java
    List<Edge> findByPageId(Long pageId);
    // 노드 ID 기반 엣지 일괄 삭제 (소스/타겟 모두 처리)
    @Modifying
    @Query("DELETE FROM Edge e WHERE e.source.id = :nodeId OR e.target.id = :nodeId")
    void deleteByNodeId(@Param("nodeId") Long nodeId);
}
