package com.example.trader.repository;

import com.example.trader.entity.Edge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EdgeRepository extends JpaRepository<Edge,Long> {
    // EdgeRepository.java
    List<Edge> findAllByPageId(Long pageId);
    // 노드 ID 기반 엣지 일괄 삭제 (소스/타겟 모두 처리)
    @Modifying
    @Query("DELETE FROM Edge e WHERE e.source.id = :nodeId OR e.target.id = :nodeId")
    void deleteByNodeId(@Param("nodeId") Long nodeId);

    boolean existsBySourceIdAndTargetId(Long sourceId, Long targetId);

    @Query("""
    select e
    from Edge e
    where e.id = :edgeId
      and e.page.id = :pageId
      and e.page.directory.user.id = :userId
    """)
    Optional<Edge> findPersonalEdge(@Param("edgeId") Long edgeId,
                                    @Param("pageId") Long pageId,
                                    @Param("userId") Long userId);

    @Query("""
    select e
    from Edge e
    where e.id = :edgeId
      and e.page.id = :pageId
      and e.page.directory.team.id = :teamId
    """)
    Optional<Edge> findByIdInTeamGraph(@Param("edgeId") Long edgeId,
                                       @Param("pageId") Long pageId,
                                       @Param("teamId") Long teamId);

    @Query("""
    select count(e) > 0
    from Edge e
    where e.page.id = :pageId
      and e.source.id = :sourceId
      and e.target.id = :targetId
    """)
    boolean existsInPageBySourceTarget(@Param("pageId") Long pageId,
                                       @Param("sourceId") Long sourceId,
                                       @Param("targetId") Long targetId);
}
