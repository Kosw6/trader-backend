package com.example.trader.dto;

/**
 * stock_info 검색 결과를 신규 Asset 식별자로 연결한 자동완성 응답.
 */
public record TickerDto(
        Long assetId,
        String ticker,
        String name,
        String englishName,
        String exchange,
        String currency,
        String assetType
) {
}
