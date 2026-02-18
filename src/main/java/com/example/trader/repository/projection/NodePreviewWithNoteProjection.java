package com.example.trader.repository.projection;

public interface NodePreviewWithNoteProjection {
    Long getId();
    Double getX();
    Double getY();
    String getSubject();
    String getContentPreview();
    String getSymb();
    java.time.LocalDate getRecordDate();
    Long getPageId();
    java.time.LocalDateTime getCreatedAt();
    java.time.LocalDateTime getModifiedAt();

    Long getNoteId();             // 링크 없으면 null
    String getNoteSubject();      // 링크 없으면 null
}
