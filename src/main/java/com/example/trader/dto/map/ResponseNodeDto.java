// NodeResponseDto.java (응답용)
package com.example.trader.dto.map;

import com.example.trader.entity.Node;
import com.example.trader.repository.projection.NodeNotesProjection;
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
public class ResponseNodeDto {
    private Long id;
    private double x;
    private double y;
    private String subject;
    private String content;
    private String symb;
    private LocalDate recordDate;
    private Map<Long, String> notes;
    private Long pageId;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    public static ResponseNodeDto toResponseDto(Node node) {
        Long pageId = (node.getPage() != null) ? node.getPage().getId() : null;

        Map<Long, String> notes = node.getNoteLinks().stream()
                .filter(link -> link.getNote() != null)
                .collect(Collectors.toMap(
                        link -> link.getNoteId(),
                        link -> link.getNoteSubject()
                ));

        return ResponseNodeDto.builder()
                .id(node.getId())
                .x(node.getX())
                .y(node.getY())
                .subject(node.getSubject())
                .content(node.getContent())
                .symb(node.getSymb())
                .recordDate(node.getRecordDate())
                .createdAt(node.getCreatedDate())
                .modifiedAt(node.getModifiedDate())
                .pageId(pageId)
                .notes(notes)
                .build();
    }

    public static ResponseNodeDto toResponseDtoWithSubstring(Node node) {
        Long pageId = (node.getPage() != null) ? node.getPage().getId() : null;

        Map<Long, String> notes = node.getNoteLinks().stream()
                .filter(link -> link.getNote() != null)
                .collect(Collectors.toMap(
                        link -> link.getNoteId(),
                        link -> link.getNoteSubject()
                ));

        String preview = null;
        if (node.getContent() != null) {
            preview = node.getContent().length() > 20
                    ? node.getContent().substring(0, 20)
                    : node.getContent();
        }

        return ResponseNodeDto.builder()
                .id(node.getId())
                .x(node.getX())
                .y(node.getY())
                .subject(node.getSubject())
                .content(preview) // ⬅️ 20자 프리뷰만 반환
                .symb(node.getSymb())
                .recordDate(node.getRecordDate())
                .createdAt(node.getCreatedDate())
                .modifiedAt(node.getModifiedDate())
                .pageId(pageId)
                .notes(notes)
                .build();
    }

    public static ResponseNodeDto toResponseDtoToPreviewList(Node node) {
        Long pageId = (node.getPage() != null) ? node.getPage().getId() : null;

        Map<Long, String> notes = node.getNoteLinks().stream()
                .filter(link -> link.getNote() != null)
                .collect(Collectors.toMap(
                        link -> link.getNoteId(),
                        link -> link.getNoteSubject()
                ));

        return ResponseNodeDto.builder()
                .id(node.getId())
                .x(node.getX())
                .y(node.getY())
                .subject(node.getSubject())
                .content(node.getContentPreview())
                .symb(node.getSymb())
                .recordDate(node.getRecordDate())
                .createdAt(node.getCreatedDate())
                .modifiedAt(node.getModifiedDate())
                .pageId(pageId)
                .notes(notes)
                .build();
    }

    //프로젝션 행 폭증이 있으므로 병합과정 필요
    public static List<ResponseNodeDto> fromProjectiontoResponseDto(List<NodePreviewWithNoteProjection> rows) {
        // 노드ID → 완성 DTO (notes는 비어있는 LinkedHashMap으로 초기화)
        Map<Long, ResponseNodeDto> byNode = new LinkedHashMap<>();

        for (var r : rows) {
            ResponseNodeDto dto = byNode.computeIfAbsent(r.getId(), id ->
                    ResponseNodeDto.builder()
                            .id(r.getId())
                            .x(r.getX() == null ? 0.0 : r.getX())
                            .y(r.getY() == null ? 0.0 : r.getY())
                            .subject(nz(r.getSubject()))
                            .content(nz(r.getContentPreview()))   // 20자 프리뷰를 content에
                            .symb(nz(r.getSymb()))
                            .recordDate(r.getRecordDate())
                            .pageId(r.getPageId())
                            .createdAt(r.getCreatedDate())
                            .modifiedAt(r.getModifiedDate())
                            .notes(new LinkedHashMap<>())         // ← 중요: 비어있는 맵을 넣어둠
                            .build()
            );

            // 이후에는 "빌더"가 아니라 "DTO"의 notes를 수정
            if (r.getNoteId() != null) {
                dto.getNotes().put(r.getNoteId(), nz(r.getNoteSubject()));
            }
        }

        return new ArrayList<>(byNode.values());
    }

    private static String nz(String s) { return s == null ? "" : s; }

    //json aggregation방식
    public static ResponseNodeDto fromProjection(NodeRowProjection p) {
        Map<Long, String> notes = new LinkedHashMap<>();

        try {
            // notes_json을 Map<Long, String> 형태로 파싱
            if (p.getNotesJson() != null && !p.getNotesJson().isBlank()) {
                ObjectMapper mapper = new ObjectMapper();

                // ✅ JSON 객체로 읽기
                Map<String, String> map = mapper.readValue(
                        p.getNotesJson(),
                        new TypeReference<Map<String, String>>() {}
                );

                // ✅ key(String) → Long으로 변환해서 notes(Map<Long,String>)으로 담기
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    notes.put(Long.valueOf(entry.getKey()), entry.getValue());
                }
            }
        } catch (Exception e) {
            // 필요시 로깅
        }

        return ResponseNodeDto.builder()
                .id(p.getId())
                .x(p.getX())
                .y(p.getY())
                .subject(p.getSubject())
                .content(p.getContent())
                .symb(p.getSymb())
                .recordDate(p.getRecordDate())
                .createdAt(p.getCreatedDate())
                .modifiedAt(p.getModifiedDate())
                .pageId(p.getPageId())
                .notes(notes)
                .build();
    }
}