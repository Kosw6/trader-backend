package com.example.trader.repository.projection;

public interface NodeNotesProjection {
    Long getNodeId();
    Double getX();
    Double getY();
    String getNodeSubject();
    Long getPageId();
    String getNotesJson();
}
