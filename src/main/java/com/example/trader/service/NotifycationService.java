package com.example.trader.service;

import com.example.trader.dto.ResponseNotifycationDto;
import com.example.trader.entity.Notifycation;
import com.example.trader.entity.NotificationRelatedType;
import com.example.trader.repository.NotifycationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotifycationService {

    private final NotifycationRepository notifycationRepository;

    /**
     * 안읽은 알림 목록 반환
     * - count를 먼저 치지 말고 그냥 조회해서 비었으면 empty 반환해도 됨(쿼리 1번으로 끝)
     * - 네이티브 View 프로젝션을 쓰는 경우: repository에서 뷰로 가져오고 DTO로 변환
     */
    @Transactional(readOnly = true)
    public List<ResponseNotifycationDto> getUnReadNotifycationList(Long userId) {
        return notifycationRepository.findUnreadViewsByUserId(userId)
                .stream()
                .map(ResponseNotifycationDto::of)
                .toList();
    }

    /**
     * 전체 알림 목록
     */
    @Transactional(readOnly = true)
    public List<ResponseNotifycationDto> getNotifycationList(Long userId) {
        return notifycationRepository.findNotifications(userId)
                .stream()
                .map(ResponseNotifycationDto::of)
                .toList();
    }

    /**
     * 팀 합류 요청 알림 생성(OWNER에게)
     * - joinRequestId 필수: relatedType=JOIN_REQUEST, relatedId=joinRequestId로 연결해야
     *   나중에 승인/거절 시 그 알림만 정확히 읽음 처리 가능
     */
    public void notifyTeamJoinRequested(
            Long ownerUserId,
            Long teamId,
            Long joinRequestId,
            Long requesterId,
            String teamName,
            String requesterNickName
    ) {
        Notifycation n = Notifycation.teamJoinRequested(
                ownerUserId,
                teamId,
                joinRequestId,
                requesterId,
                teamName,
                requesterNickName
        );
        notifycationRepository.save(n);
    }

    /**
     * 팀 합류 승인 알림(요청자에게)
     */
    public void notifyTeamJoinApproved(Long requesterId, Long teamId, String teamName) {
        notifycationRepository.save(Notifycation.teamJoinApproved(requesterId, teamId, teamName));
    }

    /**
     * 팀 합류 거절 알림(요청자에게)
     */
    public void notifyTeamJoinRejected(Long requesterId, Long teamId, String teamName) {
        notifycationRepository.save(Notifycation.teamJoinRejected(requesterId, teamId, teamName));
    }

    /**
     * OWNER가 받은 “가입요청 알림”을 읽음 처리
     * - 엔티티 컬럼: userId
     * - (relatedType=JOIN_REQUEST, relatedId=joinRequestId)로 특정
     */
    public void markJoinRequestNotificationAsRead(Long ownerId, Long joinRequestId) {
        notifycationRepository
                .findByUserIdAndRelatedTypeAndRelatedId(
                        ownerId, NotificationRelatedType.JOIN_REQUEST, joinRequestId
                )
                .ifPresent(n -> {
                    if (!n.isRead()) {
                        n.markAsRead(); // 더티체킹으로 update
                    }
                });
    }

    /**
     * (선택) 엔티티 로드 없이 바로 읽음 처리하고 싶으면 Repository update 쿼리 사용
     */
    public int markJoinRequestNotificationAsReadFast(Long ownerId, Long joinRequestId) {
        return notifycationRepository.markAsReadByRelated(
                ownerId, NotificationRelatedType.JOIN_REQUEST, joinRequestId
        );
    }

    public int markTeamNotificationAsReadFast(Long ownerId, Long teamId) {
        return notifycationRepository.markAsReadByRelated(
                ownerId, NotificationRelatedType.TEAM, teamId
        );
    }
}
