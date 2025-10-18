package com.example.trader.service;

import com.example.trader.entity.Stock;
import com.example.trader.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {
    private static final Logger log = LoggerFactory.getLogger(StockService.class);
    private final StockRepository repository;
    @Cacheable(
            cacheNames = "stockRange",
            key = "#stockName + ':' + #start.toString() + ':' + #end.toString()",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<Stock> getTimeSeriesData(LocalDateTime start, LocalDateTime end, String stockName) {
        OffsetDateTime from = toOffset(start);
        OffsetDateTime to   = toOffset(end);
//        log.info("HIT getTimeSeriesData");
        return repository.findBySymbAndTimestampBetweenOrderByTimestampAsc(stockName, from, to).orElseThrow(()->new IllegalArgumentException("존재하지 않는 주식명입니다."));
    }
    @Cacheable(
            cacheNames = "stockBefore",
            key = "#stock + ':before:' + #latestDate.toString() + ':' + #count",
            unless = "#result == null || #result.isEmpty()"
    )
    public List<Stock> getLatestDataBefore(LocalDateTime latestDate, String stock, int count) {
        OffsetDateTime cursor = toOffset(latestDate);
        List<Stock> rowsDesc = repository
                .findBySymbAndTimestampLessThanEqualOrderByTimestampDesc(
                        stock, cursor, PageRequest.of(0, Math.max(count, 1))
                ).orElseThrow(()->new IllegalArgumentException("존재하지 않는 주식명입니다.")).getContent();
        // “이전 N개”를 시간 오름차순으로 주고 싶으면 역순 정렬
//        Collections.reverse(rowsDesc);
        return rowsDesc;
    }

    @Cacheable(
            cacheNames = "stockAfter",
            key = "#stock + ':after:' + #latestDate.toString() + ':' + #count",
            unless = "#result == null || #result.isEmpty()"
    )
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
