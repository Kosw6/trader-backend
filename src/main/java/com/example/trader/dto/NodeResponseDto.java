// NodeResponseDto.java (응답용)
package com.example.trader.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NodeResponseDto {
    private Long id;
    private double x;
    private double y;
    private String subject;
    private String content;
    private String symb;
    private Long noteId;
    private Long pageId;
}