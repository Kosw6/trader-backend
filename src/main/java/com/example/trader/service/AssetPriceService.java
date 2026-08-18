package com.example.trader.service;

import com.example.trader.dto.PriceBarDto;
import com.example.trader.repository.PriceBarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AssetPriceService {

    private static final int MAX_PAGE_SIZE = 500;

    private final PriceBarRepository priceBarRepository;

    @Transactional(readOnly = true)
    public List<PriceBarDto> getBefore(Long assetId,
                                       OffsetDateTime before,
                                       int count,
                                       String source,
                                       String interval) {
        if (assetId == null || assetId <= 0) {
            throw new IllegalArgumentException("assetId must be positive");
        }
        if (before == null) {
            throw new IllegalArgumentException("before is required");
        }

        int pageSize = Math.min(Math.max(count, 1), MAX_PAGE_SIZE);
        String normalizedSource = normalize(source, "KIS");
        String normalizedInterval = normalize(interval, "1D");

        return priceBarRepository.findBefore(
                assetId,
                before,
                normalizedSource,
                normalizedInterval,
                pageSize
        );
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
