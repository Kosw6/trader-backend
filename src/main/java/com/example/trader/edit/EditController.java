package com.example.trader.edit;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/nodes")
public class EditController {

    private final DraftEditService draftEditService;
    private final EditValidateService editValidateService;

    @PostMapping("/{nodeId}/edit/start")
    public void start(@PathVariable Long nodeId,
                      @RequestParam Long groupId,
                      @RequestParam Long userId) {
        draftEditService.startEdit(groupId, nodeId, userId);
    }

    @PostMapping("/{nodeId}/edit/draft")
    public void draft(@PathVariable Long nodeId,
                      @RequestParam Long groupId,
                      @RequestParam Long userId,
                      @RequestBody Map<String, Object> patch) {
        draftEditService.updateDraft(groupId, nodeId, userId, patch);
    }

    @GetMapping("/{nodeId}/edit/validate")
    public String validate(@PathVariable Long nodeId,
                           @RequestParam Long groupId,
                           @RequestParam Long userId) {
        return editValidateService.validate(groupId, nodeId, userId);
    }
}