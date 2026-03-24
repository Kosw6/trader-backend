package com.example.trader.edit;

import com.example.trader.edit.dto.DraftEditState;
import com.example.trader.entity.Node;
import com.example.trader.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DraftEditService {

    private final NodeRepository nodeRepository;
    private final DraftRedisStore draftRedisStore;

    public void startEdit(Long groupId, Long nodeId, Long userId) {
        Node node = nodeRepository.findById(nodeId).orElseThrow();

        DraftEditState state = new DraftEditState();
        state.setGroupId(groupId);
        state.setEntityId(nodeId);
        state.setUserId(userId);
        state.setBaseVersion(node.getVersion());
        state.setDraftPatch(new HashMap<>());
        state.setDirtyFields(new HashSet<>());
        state.setServerChangedFieldsAfterEdit(new HashSet<>());

        draftRedisStore.save(state);
    }

    public void updateDraft(Long groupId, Long nodeId, Long userId, Map<String, Object> patch) {
        DraftEditState state = draftRedisStore.find(groupId, nodeId, userId);
        if (state == null) {
            throw new IllegalStateException("draft not found");
        }

        state.getDraftPatch().putAll(patch);
        state.getDirtyFields().addAll(patch.keySet());

        draftRedisStore.save(state);
    }
}