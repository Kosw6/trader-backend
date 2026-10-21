package com.example.trader.repository;

import com.example.trader.entity.GroupChatRoom;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface GroupChatRoomRepository extends JpaRepository<GroupChatRoom, Long> {
    List<GroupChatRoom> findByTeamIdOrderByCommonRoomDescIdAsc(Long teamId);
    Optional<GroupChatRoom> findByTeamIdAndCommonRoomTrue(Long teamId);
    Optional<GroupChatRoom> findByIdAndTeamId(Long id, Long teamId);
    boolean existsByTeamIdAndNameIgnoreCase(Long teamId, String name);
    long countByTeamId(Long teamId);
    long countByTeamIdAndCommonRoomFalse(Long teamId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GroupChatRoom r where r.id = :id and r.teamId = :teamId")
    Optional<GroupChatRoom> lockRoom(@Param("id") Long id, @Param("teamId") Long teamId);
}
