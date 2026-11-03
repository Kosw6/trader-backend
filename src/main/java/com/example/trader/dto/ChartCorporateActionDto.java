package com.example.trader.dto;

import java.time.LocalDate;

/** Reviewed event metadata; not an inferred price-adjustment factor. */
public record ChartCorporateActionDto(Long id, LocalDate tradingDate, String actionType,
                                     int newShares, int oldShares, String sourceUrl) {}
