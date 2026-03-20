package com.example.trader.repository;

import com.example.trader.dto.ResponseNotifycationDto;
import com.example.trader.entity.NotificationRelatedType;
import com.example.trader.entity.Notifycation;
import com.example.trader.repository.projection.NotifycationView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotifycationRepository extends JpaRepository<Notifycation,Long> {

    // 안읽은 알람 목록(뷰)
    @Query(value = """
        select
            n.id as id,
            n.message as message,
            n.is_read as isRead,
            n.notifycation_type as type,
            n.created_at as createdAt,
            n.deeplink as deeplink
        from notifycation n
        where n.user_id = :userId
          and n.is_read = false
        order by n.created_at desc
        """, nativeQuery = true)
    List<NotifycationView> findUnreadViewsByUserId(@Param("userId") Long userId);

    // 안읽은 알람 개수
    long countByUserIdAndIsReadFalse(Long userId);

    // 모든 알람 목록(뷰)
    @Query(value = """
        select
            n.id as id,
            n.message as message,
            n.is_read as isRead,
            n.notifycation_type as type,
            n.created_at as createdAt,
            n.deeplink as deeplink
        from notifycation n
        where n.user_id = :userId
        order by n.created_at desc
        """, nativeQuery = true)
    List<NotifycationView> findNotifications(@Param("userId") Long userId);

    // 단건 읽음 처리 (멱등)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Notifycation n
           set n.isRead = true
         where n.id = :id
           and n.userId = :userId
           and n.isRead = false
    """)
    int markAsRead(@Param("userId") Long userId, @Param("id") Long id);

    // cutoff 이전 모두 읽음 처리
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Notifycation n
           set n.isRead = true
         where n.userId = :userId
           and n.isRead = false
           and n.createdAt <= :cutoff
    """)
    int markAllAsReadUntil(@Param("userId") Long userId, @Param("cutoff") Instant cutoff);

    // ✅ 추가: JoinRequest 같은 "연결된 알림"을 찾기/읽음처리/중복방지에 필요
    Optional<Notifycation> findByUserIdAndRelatedTypeAndRelatedId(
            Long userId, NotificationRelatedType relatedType, Long relatedId
    );

    boolean existsByUserIdAndRelatedTypeAndRelatedId(
            Long userId, NotificationRelatedType relatedType, Long relatedId
    );



    // (선택) linked 알림을 바로 읽음처리(엔티티 로드 없이)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Notifycation n
           set n.isRead = true
         where n.userId = :userId
           and n.relatedType = :relatedType
           and n.relatedId = :relatedId
           and n.isRead = false
    """)
    int markAsReadByRelated(@Param("userId") Long userId,
                            @Param("relatedType") NotificationRelatedType relatedType,
                            @Param("relatedId") Long relatedId);

    //안읽은 알람 읽음 처리
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update Notifycation n
           set n.isRead = true
         where n.userId = :userId
           and n.isRead = false
    """)
    int markAllAsRead(@Param("userId") Long userId);
}
