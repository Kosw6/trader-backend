package com.example.trader.dto;

import com.example.trader.entity.Team;
import com.example.trader.entity.User;
import lombok.Builder;


@Builder
public record NoteDto(Long userId, Long teamId,Long id, String subject, String content, String stockSymb) {
}
