// EdgeResponseDto.java
package com.example.trader.dto.map;

import com.example.trader.entity.Edge;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@lombok.extern.jackson.Jacksonized
public class ResponseEdgeDto {
    private Long id;
    private Long sourceId;
    private Long targetId;
    private Long pageId;
    private String type;
    private String label;
    private String sourceHandle;
    private String targetHandle;
    private String variant;
    private boolean animated;
    private String stroke;
    private Integer strokeWidth;

    public static ResponseEdgeDto toResponseEdgeDto(Edge edge) {
        return ResponseEdgeDto.builder()
                .id(edge.getId())
                .sourceId(edge.getSource().getId())
                .targetId(edge.getTarget().getId())
                .type(edge.getType())
                .label(edge.getLabel())
                .sourceHandle(edge.getSourceHandle())
                .targetHandle(edge.getTargetHandle())
                .pageId(edge.getPage() != null ? edge.getPage().getId() : null)
                .variant(edge.getVariant())
                .stroke(edge.getStroke())
                .strokeWidth(edge.getStrokeWidth())
                .animated(edge.isAnimated())
                .build();
    }
}