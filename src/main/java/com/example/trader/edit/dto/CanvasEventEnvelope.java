package com.example.trader.edit.dto;

import java.util.List;

public class CanvasEventEnvelope {
    private Long groupId;
    private Long entityId;
    private Long version;
    private List<String> changedFields;

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public List<String> getChangedFields() { return changedFields; }
    public void setChangedFields(List<String> changedFields) { this.changedFields = changedFields; }
}
