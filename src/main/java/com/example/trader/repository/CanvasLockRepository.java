package com.example.trader.repository;

import com.example.trader.entity.CanvasLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CanvasLockRepository extends JpaRepository<CanvasLock, Long> {

    Optional<CanvasLock> findByTeamIdAndGraphIdAndNodeId(Long teamId, Long graphId, Long nodeId);

    List<CanvasLock> findAllByTeamIdAndGraphIdAndUserId(Long teamId, Long graphId, Long userId);

    /** 특정 노드의 만료된 락만 제거 — tryAcquire 전 호출 */
    @Modifying
    @Transactional
    @Query("DELETE FROM CanvasLock c " +
           "WHERE c.teamId = :teamId AND c.graphId = :graphId " +
           "AND c.nodeId = :nodeId AND c.expiresAt < :now")
    int deleteExpiredByNode(@Param("teamId") Long teamId,
                            @Param("graphId") Long graphId,
                            @Param("nodeId") Long nodeId,
                            @Param("now") LocalDateTime now);

    /** 전체 만료 락 일괄 제거 — 스케줄러용 */
    @Modifying
    @Transactional
    @Query("DELETE FROM CanvasLock c WHERE c.expiresAt < :now")
    int deleteAllExpired(@Param("now") LocalDateTime now);

    /** 특정 유저의 특정 노드 락 제거 */
    @Modifying
    @Transactional
    @Query("DELETE FROM CanvasLock c " +
           "WHERE c.teamId = :teamId AND c.graphId = :graphId " +
           "AND c.nodeId = :nodeId AND c.userId = :userId")
    int deleteByNodeAndUser(@Param("teamId") Long teamId,
                            @Param("graphId") Long graphId,
                            @Param("nodeId") Long nodeId,
                            @Param("userId") Long userId);

    /** 특정 유저의 전체 락 제거 */
    @Modifying
    @Transactional
    @Query("DELETE FROM CanvasLock c " +
           "WHERE c.teamId = :teamId AND c.graphId = :graphId AND c.userId = :userId")
    int deleteAllByUser(@Param("teamId") Long teamId,
                        @Param("graphId") Long graphId,
                        @Param("userId") Long userId);
}
