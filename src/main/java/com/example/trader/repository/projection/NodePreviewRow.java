package com.example.trader.repository.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface NodePreviewRow {
    Long getId();
    Double getX();
    Double getY();
    String getSubject();
    String getContentPreview(); // substring 적용
    String getSymb();
    LocalDate getRecordDate();
    LocalDateTime getModifiedDate();
}
