package com.example.trader.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 기존 차트의 JSON 필드는 유지하지만 DB 엔티티를 응답 계약으로 노출하지 않는다. */
public record StockPriceResponse(String symb, OffsetDateTime timestamp,
        BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {}
