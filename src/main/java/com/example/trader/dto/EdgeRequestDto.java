// EdgeRequestDto.java
package com.example.trader.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EdgeRequestDto {
    private Long sourceId;
    private Long targetId;
    private String type;
    private String label;
    private String sourceHandle;
    private String targetHandle;
}