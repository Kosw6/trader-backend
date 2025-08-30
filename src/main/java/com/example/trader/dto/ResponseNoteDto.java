package com.example.trader.dto;

import com.example.trader.entity.Note;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResponseNoteDto {
    private Long id;
    private Long userId;
    private String username;  // 사용자명 추가 (User 엔티티에서 가져옴)
    private Long teamId;
    private String subject;
    private String content;
    private String stockSymb;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
    private LocalDate noteDate;
    // 엔티티 -> DTO 변환 메서드
    public static ResponseNoteDto fromEntity(Note note) {
        return ResponseNoteDto.builder()
                .id(note.getId())
                .userId(note.getUser() != null ? note.getUser().getId() : null)
                .username(note.getUser() != null ? note.getUser().getUsername() : null)
                .teamId(note.getTeamId())
                .subject(note.getSubject())
                .content(note.getContent())
                .stockSymb(note.getStockSymb())
                .createdDate(note.getCreatedDate())
                .modifiedDate(note.getModifiedDate())
                .noteDate(note.getNoteDate())
                .build();
    }
}
