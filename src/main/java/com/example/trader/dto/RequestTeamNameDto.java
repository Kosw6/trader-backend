package com.example.trader.dto;

import jakarta.validation.constraints.NotBlank;

public record RequestTeamNameDto(@NotBlank String teamName) {
}
