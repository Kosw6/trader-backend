package com.example.trader.canvasagent;

import com.example.trader.common.interceptor.TeamMemberRequired;
import com.example.trader.security.details.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@TeamMemberRequired
@RequiredArgsConstructor
@RequestMapping("/api/teams/{teamId}/graphs/{graphId}/ai-agent")
public class CanvasAgentController {
    private final CanvasAgentService service;
    @GetMapping("/snapshot")
    public Map<String,Object> snapshot(@AuthenticationPrincipal UserContext user,@PathVariable long teamId,@PathVariable long graphId) {
        return service.snapshot(user.getUserDto().getId(),teamId,graphId);
    }
    @PostMapping("/claim")
    public Map<String,Object> claim(@AuthenticationPrincipal UserContext user,@PathVariable long teamId,@PathVariable long graphId,@Valid @RequestBody CanvasAgentContracts.Claim body) {
        return service.claim(user.getUserDto().getId(),teamId,graphId,body);
    }
    @PostMapping("/apply")
    public Map<String,Object> apply(@AuthenticationPrincipal UserContext user,@PathVariable long teamId,@PathVariable long graphId,@Valid @RequestBody CanvasAgentContracts.Apply body) {
        return service.apply(user.getUserDto().getId(),teamId,graphId,body);
    }
    @GetMapping("/runs/{id}")
    public Map<String,Object> status(@AuthenticationPrincipal UserContext user,@PathVariable long teamId,@PathVariable long graphId,@PathVariable UUID id) {
        return service.status(user.getUserDto().getId(),teamId,graphId,id);
    }
    @PostMapping("/runs/{id}/cancel")
    public Map<String,Object> cancel(@AuthenticationPrincipal UserContext user,@PathVariable long teamId,@PathVariable long graphId,@PathVariable UUID id) {
        return service.cancel(user.getUserDto().getId(),teamId,graphId,id);
    }
    @PostMapping("/runs/{id}/undo")
    public Map<String,Object> undo(@AuthenticationPrincipal UserContext user,@PathVariable long teamId,@PathVariable long graphId,@PathVariable UUID id) {
        return service.undo(user.getUserDto().getId(),teamId,graphId,id);
    }
}
