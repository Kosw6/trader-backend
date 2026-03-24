package com.example.trader.ws.raw;

import com.example.trader.ws.raw.dto.RawCursorMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/ws-test")
@RequiredArgsConstructor
public class WsRawTestPublishController {

    private final RawPresenceBroadcaster broadcaster;
    private final CanvasSessionRegistry registry;

    @Value("${app.instance-id:unknown}")
    private String instanceId;

    @PostMapping("/broadcast/latest")
    public PublishResponse publishLatest(@RequestBody PublishRequest request) {
        String roomKey = registry.roomKey(request.teamId(), request.graphId());

        RawCursorMessage msg = new RawCursorMessage(
                request.type() == null ? "CURSOR" : request.type(),
                request.teamId(),
                request.graphId(),
                request.userId() == null ? -1L : request.userId(),
                request.nickName() == null ? "system-" + instanceId : request.nickName(),
                request.nodeId(),
                request.x() == null ? 0.0 : request.x(),
                request.y() == null ? 0.0 : request.y(),
                System.currentTimeMillis()
        );

        String latestKey = makeLatestKey(msg.type(), msg.userId(), msg.nodeId());
        broadcaster.publishLatest(roomKey, latestKey, msg);

        return new PublishResponse(
                instanceId,
                roomKey,
                "latest",
                registry.size(roomKey)
        );
    }

    @PostMapping("/broadcast/reliable")
    public PublishResponse publishReliable(@RequestBody PublishRequest request) {
        String roomKey = registry.roomKey(request.teamId(), request.graphId());

        RawCursorMessage msg = new RawCursorMessage(
                request.type() == null ? "__CONTROL__" : request.type(),
                request.teamId(),
                request.graphId(),
                request.userId() == null ? -1L : request.userId(),
                request.nickName() == null ? "system-" + instanceId : request.nickName(),
                request.nodeId(),
                request.x() == null ? 0.0 : request.x(),
                request.y() == null ? 0.0 : request.y(),
                System.currentTimeMillis()
        );

        broadcaster.publishReliable(roomKey, msg);

        return new PublishResponse(
                instanceId,
                roomKey,
                "reliable",
                registry.size(roomKey)
        );
    }

    private String makeLatestKey(String type, Long userId, Long nodeId) {
        if ("CURSOR".equals(type)) {
            return type + ":" + userId;
        }
        if ("DRAG_PREVIEW".equals(type)) {
            return type + ":" + userId + ":" + (nodeId != null ? nodeId : 0L);
        }
        return type + ":" + userId;
    }

    public record PublishRequest(
            Long teamId,
            Long graphId,
            String type,
            Long userId,
            String nickName,
            Long nodeId,
            Double x,
            Double y
    ) {
    }

    public record PublishResponse(
            String instanceId,
            String roomKey,
            String mode,
            int roomSize
    ) {
    }
}
