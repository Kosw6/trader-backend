// NodeResponseDto.java (응답용)
package com.example.trader.dto.map;

import com.example.trader.entity.Node;
import com.example.trader.entity.NodeNoteLink;
import com.example.trader.repository.projection.NodePreviewWithNoteProjection;
import com.example.trader.repository.projection.NodeRowProjection;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Builder
@lombok.extern.jackson.Jacksonized
public class ResponseNodeDto {

    private Long id;
    private double x;
    private double y;
    private String subject;

    // 전체 content
    private String content;

    private String symb;
    private LocalDate recordDate;
    private Map<Long, String> notes;
    private List<LinkedNoteDto> linkedNotes;
    private Long pageId;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private int version;

    public static ResponseNodeDto from(Node node) {
        return from(node, null);
    }

    public static ResponseNodeDto from(Node node, Long requesterId) {
        Long pageId = node.getPage() != null ? node.getPage().getId() : null;

        Map<Long, String> notes = node.getNoteLinks().stream()
                .filter(link -> link.getNote() != null)
                .collect(Collectors.toMap(
                        NodeNoteLink::getNoteId,
                        NodeNoteLink::getNoteSubject,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        List<LinkedNoteDto> linkedNotes = node.getNoteLinks().stream()
                .filter(link -> link.getNote() != null)
                .map(link -> {
                    var owner = link.getNote().getUser();
                    Long ownerId = owner != null ? owner.getId() : null;
                    String ownerName = owner == null
                            ? "알 수 없는 사용자"
                            : (owner.getNickName() == null || owner.getNickName().isBlank()
                            ? owner.getUsername()
                            : owner.getNickName());
                    return new LinkedNoteDto(
                            link.getNote().getId(), link.getNote().getSubject(),
                            ownerId, ownerName, requesterId != null && requesterId.equals(ownerId)
                    );
                })
                .sorted(Comparator.comparing(LinkedNoteDto::ownerName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LinkedNoteDto::noteId))
                .toList();

        return ResponseNodeDto.builder()
                .id(node.getId())
                .x(node.getX())
                .y(node.getY())
                .subject(node.getSubject())
                .content(node.getContent())
                .symb(node.getSymb())
                .recordDate(node.getRecordDate())
                .pageId(pageId)
                .createdAt(node.getCreatedAt())
                .modifiedAt(node.getModifiedAt())
                .version(node.getVersion())
                .notes(notes)
                .linkedNotes(linkedNotes)
                .build();
    }

    public record LinkedNoteDto(Long noteId, String subject, Long ownerId, String ownerName, boolean editable) {}
}
