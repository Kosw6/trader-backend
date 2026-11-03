package com.example.trader.controller;
import com.example.trader.security.details.UserContext;
import com.example.trader.service.PrivateCanvasSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/canvas-snapshots")
@RequiredArgsConstructor
public class PrivateCanvasSnapshotController {
    private final PrivateCanvasSnapshotService service;
    @GetMapping
    public List<PrivateCanvasSnapshotService.Summary> list(@AuthenticationPrincipal UserContext user,@RequestParam(required=false) Long teamId) {
        return service.list(user.getUserDto().getId(),teamId);
    }
    @GetMapping("/{id}")
    public PrivateCanvasSnapshotService.Detail get(@AuthenticationPrincipal UserContext user,@PathVariable UUID id) {
        return service.get(user.getUserDto().getId(),id);
    }
    @PostMapping
    public PrivateCanvasSnapshotService.Detail save(@AuthenticationPrincipal UserContext user,@RequestBody PrivateCanvasSnapshotService.SaveRequest request) {
        return service.save(user.getUserDto().getId(),request);
    }
    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal UserContext user,@PathVariable UUID id) { service.delete(user.getUserDto().getId(),id); }
    @PostMapping("/{id}/copy")
    public PrivateCanvasSnapshotService.CopyResult copy(@AuthenticationPrincipal UserContext user,@PathVariable UUID id,@RequestBody PrivateCanvasSnapshotService.CopyRequest request) {
        return service.copy(user.getUserDto().getId(),id,request);
    }
}
