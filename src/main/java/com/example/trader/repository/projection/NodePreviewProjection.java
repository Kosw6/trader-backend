package com.example.trader.repository.projection;

public interface NodePreviewProjection {
    Long getId();
    Double getX();
    Double getY();
    String getSubject();
    String getContentPreview();   // ← 뷰의 LEFT(content, 20)
    Long getPageId();
    java.time.LocalDateTime getCreatedDate();
    java.time.LocalDateTime getModifiedDate();
}
