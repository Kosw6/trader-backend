package com.example.trader.dto.map;

import com.example.trader.entity.NodeNoteLink;
import com.example.trader.entity.NodeSummary;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
@lombok.extern.jackson.Jacksonized
public class ResponseNodeSummaryDto {

    private Long id;
    private double x;
    private double y;
    private String subject;

    // 전체 content 아님
    private String contentPreview;

    private String symb;
    private LocalDate recordDate;
    private Map<Long, String> notes;
    private Long pageId;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static ResponseNodeSummaryDto from(NodeSummary node) {
        Map<Long, String> notes = node.getNoteLinks().stream()
                .collect(Collectors.toMap(
                        NodeNoteLink::getNoteId,
                        NodeNoteLink::getNoteSubject,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        return ResponseNodeSummaryDto.builder()
                .id(node.getId())
                .x(node.getX())
                .y(node.getY())
                .subject(node.getSubject())
                .contentPreview(node.getContentPreview())
                .symb(node.getSymb())
                .recordDate(node.getRecordDate())
                .pageId(node.getPageId())
                .createdAt(node.getCreatedAt())
                .modifiedAt(node.getModifiedAt())
                .notes(notes)
                .build();
    }
}