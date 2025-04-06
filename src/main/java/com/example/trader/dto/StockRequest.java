package com.example.trader.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class StockRequest {
    public StockRequest() {
    }
    LocalDateTime start;
    LocalDateTime end;
    String stockName;
}
