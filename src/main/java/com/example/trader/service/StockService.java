package com.example.trader.service;

import com.example.trader.entity.Stock;
import com.example.trader.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {
    private static final Logger log = LoggerFactory.getLogger(StockService.class);
    private final StockRepository repository;

    @Transactional(readOnly = true)
    public List<Stock> getTimeSeriesData(LocalDateTime start, LocalDateTime end, String stockName) {
        OffsetDateTime from = toOffset(start);
        OffsetDateTime to   = toOffset(end);
        return repository.findBySymbAndTimestampBetweenOrderByTimestampAsc(stockName, from, to).orElseThrow(()->new IllegalArgumentException(“존재하지 않는 주식명입니다.”));
    }

    @Transactional(readOnly = true)
    public List<Stock> getLatestDataBefore(LocalDateTime latestDate, String stock, int count) {
        OffsetDateTime cursor = toOffset(latestDate);
        List<Stock> rowsDesc = repository
                .findBySymbAndTimestampLessThanEqualOrderByTimestampDesc(
                        stock, cursor, PageRequest.of(0, Math.max(count, 1))
                ).orElseThrow(()->new IllegalArgumentException(“존재하지 않는 주식명입니다.”)).getContent();
        return rowsDesc;
    }

    @Transactional(readOnly = true)
    public List<Stock> getLatestDataAfter(LocalDateTime latestDate, String stock, int count) {
        OffsetDateTime cursor = toOffset(latestDate);
        return repository
                .findBySymbAndTimestampGreaterThanEqualOrderByTimestampAsc(
                        stock, cursor, PageRequest.of(0, Math.max(count, 1))
                ).orElseThrow(()->new IllegalArgumentException("존재하지 않는 주식명입니다."))
                .getContent();
    }


    private static OffsetDateTime toOffset(LocalDateTime ldt) {
        return ldt.atOffset(ZoneOffset.of("+09:00")); // 환경에 맞게 조정
    }
}
