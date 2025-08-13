package com.example.trader.repository;

import com.example.trader.entity.Stock;

import com.example.trader.entity.StockId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface StockRepository extends JpaRepository<Stock, StockId> {
    List<Stock> findBySymbAndTimestampBetweenOrderByTimestampAsc(
            String symb, OffsetDateTime from, OffsetDateTime to);

    List<Stock> findBySymbAndTimestampBetweenOrderByTimestampDesc(
            String symb, OffsetDateTime from, OffsetDateTime to);
    // 기준일자 "이전" 데이터 (desc 정렬 + 페이지 사이즈로 상위 N개)
    Page<Stock> findBySymbAndTimestampLessThanEqualOrderByTimestampDesc(
            String symb, OffsetDateTime cursor, Pageable pageable);

    // 기준일자 "이후" 데이터 (asc 정렬 + 페이지 사이즈로 상위 N개)
    Page<Stock> findBySymbAndTimestampGreaterThanEqualOrderByTimestampAsc(
            String symb, OffsetDateTime cursor, Pageable pageable);

}
