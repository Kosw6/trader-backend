// NodeResponseDto.java (응답용)
package com.example.trader.dto.map;

import com.example.trader.entity.Node;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Builder
public class ResponseNodeDto {
    private Long id;
    private double x;
    private double y;
    private String subject;
    private String content;
    private String symb;
    private LocalDate recordDate;
    private Set<Long> noteIds;
    private Long pageId;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    public static ResponseNodeDto toResponseDto(Node node) {
        Long pageId = (node.getPage() != null) ? node.getPage().getId() : null;

        Set<Long> noteIds = node.getNoteLinks().stream()
                .map(link -> link.getNote().getId())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        return ResponseNodeDto.builder()
                .id(node.getId())
                .x(node.getX())
                .y(node.getY())
                .subject(node.getSubject())
                .content(node.getContent())
                .symb(node.getSymb())
                .recordDate(node.getRecordDate())   // ← 추가
                .pageId(pageId)
                .noteIds(noteIds)
                .build();
    }
}