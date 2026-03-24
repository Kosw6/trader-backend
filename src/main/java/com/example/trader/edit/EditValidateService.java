package com.example.trader.edit;

import com.example.trader.edit.dto.DraftEditState;

import com.example.trader.entity.Node;
import com.example.trader.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EditValidateService {

    private final DraftRedisStore draftRedisStore;
    private final NodeRepository nodeRepository;

    public String validate(Long groupId, Long nodeId, Long userId) {
        DraftEditState draft = draftRedisStore.find(groupId, nodeId, userId);
        if (draft == null) throw new IllegalStateException("draft not found");

        Node node = nodeRepository.findById(nodeId).orElseThrow();

        if (draft.getBaseVersion().equals(node.getVersion())) {
            return "SAFE";
        }

        Set<String> conflict = new HashSet<>(draft.getDirtyFields());
        conflict.retainAll(draft.getServerChangedFieldsAfterEdit());

        return conflict.isEmpty() ? "AUTO_MERGE" : "CONFLICT";
    }
}