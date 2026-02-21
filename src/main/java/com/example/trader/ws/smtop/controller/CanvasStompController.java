package com.example.trader.ws.smtop.controller;

import com.example.trader.ws.smtop.dto.CursorMessage;
import com.example.trader.ws.smtop.dto.LockMessage;
import com.example.trader.ws.smtop.dto.NodeMoveMessage;
import com.example.trader.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class CanvasStompController {

    private final SimpMessagingTemplate messagingTemplate;
    private final NodeService nodeService;

    /**
     * 클라 SEND:
     * /app/teams/{teamId}/graphs/{graphId}/presence/cursor
     * 서버 BROADCAST:
     * /topic/teams/{teamId}/graphs/{graphId}/presence
     */
//    @MessageMapping("/teams/{teamId}/graphs/{graphId}/presence/cursor")
//    public void cursor(
//            @DestinationVariable Long teamId,
//            @DestinationVariable Long graphId,
//            @Payload CursorMessage msg
//    ) {
//        // TODO: 권한 체크(팀 멤버인지) / throttling(20~30Hz) 등을 여기에 넣을 수 있음
//
//        String topic = "/topic/teams/" + teamId + "/graphs/" + graphId + "/presence";
//        messagingTemplate.convertAndSend(topic, msg);
//    }

    @MessageMapping("/teams/{teamId}/graphs/{graphId}/presence/cursor")
    public void cursor(@DestinationVariable Long teamId,
                       @DestinationVariable Long graphId,
                       @Payload CursorMessage msg,
                       Principal principal) {

//        msg.username(principal.getName()); // CursorMessage에 sender 필드 추가 권장
        messagingTemplate.convertAndSend(
                "/topic/teams/" + teamId + "/graphs/" + graphId + "/presence",
                msg
        );
    }

    /**
     * 노드 이동 프리뷰(드래그 중)도 일단 STOMP로.
     * SEND: /app/teams/{teamId}/graphs/{graphId}/nodes/move-preview
     * BROADCAST: /topic/teams/{teamId}/graphs/{graphId}/nodes
     * 드래그 드랍시에는 http로 확정
     */
    @MessageMapping("/teams/{teamId}/graphs/{graphId}/nodes/move-preview")
    public void nodeMovePreview(
            @DestinationVariable Long teamId,
            @DestinationVariable Long graphId,
            @Payload NodeMoveMessage msg
    ) {

        messagingTemplate.convertAndSend(
                "/topic/teams/" + teamId + "/graphs/" + graphId + "/nodes/move-preview",
                msg
        );
    }

    /**
     * 락 획득/해제
     * SEND: /app/teams/{teamId}/graphs/{graphId}/locks
     * BROADCAST: /topic/teams/{teamId}/graphs/{graphId}/locks
     */
    @MessageMapping("/teams/{teamId}/graphs/{graphId}/locks")
    public void lock(
            @DestinationVariable Long teamId,
            @DestinationVariable Long graphId,
            @Payload LockMessage msg
    ) {
        // TODO: 실제 락 로직(메모리/DB/낙관락) 및 권한 체크
        String topic = "/topic/teams/" + teamId + "/graphs/" + graphId + "/locks";
        messagingTemplate.convertAndSend(topic, msg);
    }
}

