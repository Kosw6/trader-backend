package com.example.trader.service;

import com.example.trader.dto.StockPriceResponse;
import com.example.trader.repository.PriceBarQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {
    private final PriceBarQueryRepository repository;

    public List<StockPriceResponse> getTimeSeriesData(LocalDateTime start, LocalDateTime end, String stockName) {
        return repository.between(stockName, toOffset(start), toOffset(end));
    }

    public List<StockPriceResponse> getLatestDataBefore(LocalDateTime latestDate, String stock, int count) {
        return repository.before(stock, toOffset(latestDate), Math.max(count, 1));
    }

    public List<StockPriceResponse> getLatestDataAfter(LocalDateTime latestDate, String stock, int count) {
        return repository.after(stock, toOffset(latestDate), Math.max(count, 1));
    }

    private static OffsetDateTime toOffset(LocalDateTime date) {
        // 기존 API의 시간대 계약 유지. DB 원본 시각은 TIMESTAMPTZ로 비교한다.
        return date.atOffset(ZoneOffset.ofHours(9));
    }
}
