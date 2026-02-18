package com.example.trader.dto;

import com.example.trader.entity.Team;
import com.example.trader.entity.User;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;


@Builder
public record NoteDto(@NotBlank(message = "유저ID는 필수입니다.") Long userId, Long teamId, Long id, String subject, String content, String stockSymb) {
}
