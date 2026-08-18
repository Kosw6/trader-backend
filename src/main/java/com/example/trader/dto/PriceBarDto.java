package com.example.trader.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 차트용 일봉 응답. timestamp 이름은 기존 프론트 차트 계약을 유지한다.
 */
public record PriceBarDto(
        Long assetId,
        OffsetDateTime timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal adjustedClose,
        BigDecimal volume,
        String currency,
        String source,
        String interval
) {
}
