package com.example.trader.repository;

import com.example.trader.entity.JoinRequest;
import com.example.trader.entity.JoinRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {

    Optional<JoinRequest> findByRequesterIdAndTeamIdAndStatus(Long requesterId, Long teamId, JoinRequestStatus status);

    List<JoinRequest> findByTeamIdAndStatusOrderByCreatedAtDesc(Long teamId, JoinRequestStatus status);

    boolean existsByRequesterIdAndTeamIdAndStatus(Long requesterId, Long teamId, JoinRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select jr
    from JoinRequest jr
    join fetch jr.team t
    join fetch jr.requester r
    where jr.id = :id
      and jr.status = com.example.trader.entity.JoinRequestStatus.PENDING
    """)
    Optional<JoinRequest> findPendingWithTeamAndRequester(@Param("id") Long id);

    //대기 상태인 요청을 삭제
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        delete from JoinRequest jr
        where jr.requester.id = :requesterId
          and jr.team.id = :teamId
          and jr.status = com.example.trader.entity.JoinRequestStatus.PENDING
    """)
    int deletePending(@Param("requesterId") Long requesterId, @Param("teamId") Long teamId);

    //편의 메서드
    default Optional<JoinRequest> findPending(Long requesterId, Long teamId) {
        return findByRequesterIdAndTeamIdAndStatus(requesterId, teamId, JoinRequestStatus.PENDING);
    }

    // 팀별 대기 요청 목록 (오너용)
    @Query("""
        select jr
        from JoinRequest jr
        join fetch jr.requester r
        where jr.team.id = :teamId
          and jr.status = com.example.trader.entity.JoinRequestStatus.PENDING
        order by jr.createdAt desc
    """)
    List<JoinRequest> findPendingByTeamId(@Param("teamId") Long teamId);

}
