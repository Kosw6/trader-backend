package com.example.trader.service;

import com.example.trader.dto.PriceBarDto;
import com.example.trader.dto.PriceChartSnapshotDto;
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
    public List<com.example.trader.dto.ChartCorporateActionDto> getCorporateActions(Long assetId) {
        if(assetId==null || assetId<=0) throw new IllegalArgumentException("유효한 종목 ID가 필요합니다.");
        return priceBarRepository.corporateActions(assetId);
    }

    @Transactional(readOnly = true)
    public List<PriceBarDto> getBefore(Long assetId,
                                       OffsetDateTime before,
                                       int count,
                                       String source,
                                       String interval) {
        return getBefore(assetId,before,count,source,interval,null);
    }

    @Transactional(readOnly = true)
    public List<PriceChartSnapshotDto> getSnapshots(Long assetId) {
        if(assetId==null || assetId<=0) throw new IllegalArgumentException("유효한 종목 ID가 필요합니다.");
        return priceBarRepository.chartSnapshots(assetId);
    }

    @Transactional(readOnly = true)
    public List<PriceBarDto> getBefore(Long assetId, OffsetDateTime before, int count,
                                      String source, String interval, Long snapshotId) {
        if (assetId == null || assetId <= 0) {
            throw new IllegalArgumentException("assetId must be positive");
        }
        if (before == null) {
            throw new IllegalArgumentException("before is required");
        }

        int pageSize = Math.min(Math.max(count, 1), MAX_PAGE_SIZE);
        String normalizedSource = normalize(source, "KIS");
        String normalizedInterval = normalize(interval, "1D");
        if (!List.of("1D", "1W", "1M", "1Y").contains(normalizedInterval)) {
            throw new IllegalArgumentException("조회 주기는 1D, 1W, 1M, 1Y 중 하나여야 합니다.");
        }
        if(snapshotId != null) {
            if(snapshotId<=0) throw new IllegalArgumentException("유효한 검토본 ID가 필요합니다.");
            return priceBarRepository.findSnapshotBefore(assetId,before,normalizedSource,normalizedInterval,pageSize,snapshotId);
        }

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
