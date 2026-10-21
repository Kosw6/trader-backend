package com.example.trader.repository;

import com.example.trader.entity.GroupChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface GroupChatMessageRepository extends JpaRepository<GroupChatMessage, Long> {
    Optional<GroupChatMessage> findByRoomIdAndSenderIdAndClientMessageId(Long roomId, Long senderId, String clientMessageId);

    @Query("select m from GroupChatMessage m where m.roomId = :roomId and (:beforeId is null or m.id < :beforeId) order by m.id desc")
    List<GroupChatMessage> history(@Param("roomId") Long roomId, @Param("beforeId") Long beforeId, Pageable limit);
}
