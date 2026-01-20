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
    java.time.LocalDateTime getCreatedDate();
    java.time.LocalDateTime getModifiedDate();

    Long getNoteId();             // 링크 없으면 null
    String getNoteSubject();      // 링크 없으면 null
}
