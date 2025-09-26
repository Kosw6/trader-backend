// EdgeResponseDto.java
package com.example.trader.dto.map;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
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
}