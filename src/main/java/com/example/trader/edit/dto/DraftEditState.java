package com.example.trader.edit.dto;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DraftEditState {
    private Long groupId;
    private Long entityId;
    private Long userId;

    private Long baseVersion;
    private Map<String, Object> draftPatch = new HashMap<>();
    private Set<String> dirtyFields = new HashSet<>();
    private Set<String> serverChangedFieldsAfterEdit = new HashSet<>();

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getBaseVersion() { return baseVersion; }
    public void setBaseVersion(Long baseVersion) { this.baseVersion = baseVersion; }

    public Map<String, Object> getDraftPatch() { return draftPatch; }
    public void setDraftPatch(Map<String, Object> draftPatch) { this.draftPatch = draftPatch; }

    public Set<String> getDirtyFields() { return dirtyFields; }
    public void setDirtyFields(Set<String> dirtyFields) { this.dirtyFields = dirtyFields; }

    public Set<String> getServerChangedFieldsAfterEdit() { return serverChangedFieldsAfterEdit; }
    public void setServerChangedFieldsAfterEdit(Set<String> serverChangedFieldsAfterEdit) {
        this.serverChangedFieldsAfterEdit = serverChangedFieldsAfterEdit;
    }
}