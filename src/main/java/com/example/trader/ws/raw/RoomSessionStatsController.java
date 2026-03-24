package com.example.trader.ws.raw;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequiredArgsConstructor
public class RoomSessionStatsController {

    private final CanvasSessionRegistry registry;

    @Value("${app.instance-id:unknown}")
    private String instanceId;

    @GetMapping("/internal/ws/room-size")
    public RoomSizeResponse roomSize(
            @RequestParam Long teamId,
            @RequestParam Long graphId
    ) {
        String roomKey = registry.roomKey(teamId, graphId);
        return new RoomSizeResponse(
                instanceId,
                roomKey,
                registry.size(roomKey)
        );
    }

    @GetMapping("/internal/ws/rooms")
    public RoomsResponse rooms() {
        return new RoomsResponse(
                instanceId,
                registry.roomKeysSnapshot()
        );
    }

    public record RoomSizeResponse(
            String instanceId,
            String roomKey,
            int size
    ) {
    }

    public record RoomsResponse(
            String instanceId,
            Set<String> roomKeys
    ) {
    }
}
