package com.example.trader.dto;

import com.example.trader.entity.Ticker;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;


public record TickerDto(String symb, String name, String eName) {
}
