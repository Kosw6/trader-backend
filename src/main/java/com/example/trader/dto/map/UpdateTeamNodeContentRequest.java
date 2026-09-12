package com.example.trader.dto.map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.List;

public final class UpdateTeamNodeContentRequest extends NodeContentUpdateRequest {
    private List<String> dirtyFields;
    private Integer baseVersion;
    private Boolean force;

    public List<String> getDirtyFields() { return dirtyFields; }

    @JsonSetter("dirtyFields")
    public void setDirtyFields(List<String> dirtyFields) {
        this.dirtyFields = dirtyFields;
    }

    public Integer getBaseVersion() { return baseVersion; }

    @JsonSetter("baseVersion")
    public void setBaseVersion(Integer baseVersion) {
        this.baseVersion = baseVersion;
    }

    public Boolean getForce() { return force; }

    /** @deprecated 충돌 해결은 확인한 currentVersion을 새 baseVersion으로 제출한다. */
    @Deprecated
    @JsonSetter("force")
    public void setForce(Boolean force) {
        this.force = force;
    }

    @JsonIgnore
    @Deprecated
    public boolean isForce() {
        return Boolean.TRUE.equals(force);
    }
}
