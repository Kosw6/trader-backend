package com.example.trader.dto;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** collectedAt은 수집 시각이며 공급자가 보증한 조정 기준일을 뜻하지 않는다. */
public record PriceChartSnapshotDto(Long id, Long assetId, String source, String currency,
    String basis, String volumeBasis, LocalDate coverageStart, LocalDate coverageEnd,
    OffsetDateTime collectedAt, int rowCount, boolean defaultForChart) {}
