package com.example.trader.dto;

import lombok.Builder;

@Builder
public record RequestNoteDto(String subject, String content, String stockSymb,Long teamId,Long noteId,Long id) {
}