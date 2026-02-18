package com.example.trader.repository.projection;

public interface NodeRowProjection {
    Long getId();
    Double getX();
    Double getY();
    String getSubject();
    String getContent();
    String getSymb();
    java.time.LocalDate getRecordDate();
    Long getPageId();
    java.time.LocalDateTime getCreatedAt();
    java.time.LocalDateTime getModifiedAt();
    String getNotesJson(); // json/jsonb 문자열
}
