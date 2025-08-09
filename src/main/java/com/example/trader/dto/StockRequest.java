package com.example.trader.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class StockRequest {
    public StockRequest() {
    }
    @Schema(description = "검색 시작일", example = "2024-01-01T00:00:00")
    LocalDateTime start;
    @Schema(description = "검색 종료일", example = "2024-01-30T00:00:00")
    LocalDateTime end;
    @Schema(description = "주식명", example = "TSLA")
    String stockName;
}
