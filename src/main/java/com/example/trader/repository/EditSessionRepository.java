package com.example.trader.repository;

import com.example.trader.entity.EditSessionEntity;
import com.example.trader.entity.EditSessionId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EditSessionRepository extends JpaRepository<EditSessionEntity, EditSessionId> {

    @EntityGraph(attributePaths = "dirtyFields")
    Optional<EditSessionEntity> findWithDirtyFieldsByTeamIdAndGraphIdAndNodeIdAndUserId(
            Long teamId,
            Long graphId,
            Long nodeId,
            Long userId
    );

    List<EditSessionEntity> findTop100ByUpdatedAtBeforeOrderByUpdatedAtAsc(LocalDateTime cutoff);
}
