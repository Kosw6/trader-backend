package com.example.trader.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "edit_session")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(EditSessionId.class)
public class EditSessionEntity {

    @Id
    private Long teamId;

    @Id
    private Long graphId;

    @Id
    private Long nodeId;

    @Id
    private Long userId;

    private Integer baseVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String draftData; // JSON string

    @ElementCollection
    @CollectionTable(name = "edit_session_dirty_fields")
    private List<String> dirtyFields;

    private LocalDateTime updatedAt;

    // 🔥 핵심 update 메서드
    public void updateDraft(String draftData, List<String> dirtyFields) {
        this.draftData = draftData;
        this.dirtyFields = dirtyFields;
        this.updatedAt = LocalDateTime.now();
    }
}
