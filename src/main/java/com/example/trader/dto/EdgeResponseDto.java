// EdgeResponseDto.java
package com.example.trader.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EdgeResponseDto {
    private Long id;
    private Long sourceId;
    private Long targetId;
    private Long pageId;
    private String type;
    private String label;
    private String sourceHandle;
    private String targetHandle;
}