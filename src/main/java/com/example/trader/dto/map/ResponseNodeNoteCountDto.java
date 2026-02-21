package com.example.trader.dto.map;

import com.example.trader.entity.Node;
import com.example.trader.repository.projection.NodePreviewWithNoteProjection;
import com.example.trader.repository.projection.NodeRowProjection;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Builder
public class ResponseNodeNoteCountDto {
    private Long id;
    private double x;
    private double y;
    private String subject;
    private String content;
    private String symb;
    private LocalDate recordDate;
    private Integer noteCount;
    private Long pageId;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

//    public static ResponseNodeNoteCountDto toResponseDtoToPreviewList(Node node) {
//        Long pageId = (node.getPage() != null) ? node.getPage().getId() : null;
//
//        Map<Long, String> notes = node.getNoteLinks().stream()
//                .filter(link -> link.getNote() != null)
//                .collect(Collectors.toMap(
//                        link -> link.getNoteId(),
//                        link -> link.getNoteSubject()
//                ));
//
//        return ResponseNodeNoteCountDto.builder()
//                .id(node.getId())
//                .x(node.getX())
//                .y(node.getY())
//                .subject(node.getSubject())
//                .content(node.getContentPreview())
//                .symb(node.getSymb())
//                .recordDate(node.getRecordDate())
//                .createdAt(node.getCreatedAt())
//                .modifiedAt(node.getModifiedAt())
//                .pageId(pageId)
//                .noteCount()
//                .build();
//    }
}
