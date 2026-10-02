package com.example.trader.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;
import java.util.Date;

@Builder
public record RequestNoteDto(@NotBlank(message = "제목을 작성해주세요") String subject, @NotNull(message = "내용은 null일 수 없습니다") String content,
                             String stockSymb,
                             @NotNull(message = "기록 날짜를 입력해주세요") LocalDate noteDate, Long teamId, Long noteId, Long id) {
}
