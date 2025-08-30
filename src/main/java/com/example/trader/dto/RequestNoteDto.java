package com.example.trader.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.LocalDate;
import java.util.Date;

@Builder
public record RequestNoteDto(@NotBlank(message = "제목을 작성해주세요") String subject, @NotBlank(message = "내용을 작성해주세요") String content, String stockSymb,
                             LocalDate noteDate, Long teamId, Long noteId, Long id) {
}