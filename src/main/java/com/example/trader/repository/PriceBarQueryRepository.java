package com.example.trader.repository;

import com.example.trader.dto.StockPriceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/** 레거시 차트 계약은 KIS 일봉이다. 다른 제공자/주기를 합쳐 중복 봉을 만들지 않는다. */
@Repository
@RequiredArgsConstructor
public class PriceBarQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String SELECT = """
            SELECT :symbol AS symb, bar_time, open, high, low, close, volume
            FROM price_bar
            WHERE asset_id = :assetId AND source = 'KIS' AND "interval" = '1D'
            """;

    private MapSqlParameterSource parameters(String symbol) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("종목명이 필요합니다.");
        String normalized = symbol.strip().toUpperCase(Locale.ROOT);
        var p = new MapSqlParameterSource("symbol", normalized);
        var ids = jdbc.queryForList("""
                SELECT DISTINCT asset_id FROM asset_provider_symbol
                WHERE provider = 'KIS' AND provider_symbol = :symbol AND is_current = TRUE
                """, p, Long.class);
        // 거래소가 다른 동명 티커를 임의로 합치지 않는다.
        if (ids.size() != 1) throw new IllegalArgumentException("종목을 유일하게 찾을 수 없습니다.");
        return p.addValue("assetId", ids.get(0));
    }

    public List<StockPriceResponse> between(String symbol, OffsetDateTime from, OffsetDateTime to) {
        return query(" AND bar_time BETWEEN :from AND :to ORDER BY bar_time ASC",
                parameters(symbol).addValue("from", from).addValue("to", to));
    }

    public List<StockPriceResponse> before(String symbol, OffsetDateTime cursor, int count) {
        return query(" AND bar_time <= :cursor ORDER BY bar_time DESC LIMIT :count",
                parameters(symbol).addValue("cursor", cursor).addValue("count", count));
    }

    public List<StockPriceResponse> after(String symbol, OffsetDateTime cursor, int count) {
        return query(" AND bar_time >= :cursor ORDER BY bar_time ASC LIMIT :count",
                parameters(symbol).addValue("cursor", cursor).addValue("count", count));
    }

    private List<StockPriceResponse> query(String suffix, MapSqlParameterSource p) {
        return jdbc.query(SELECT + suffix, p, (rs, row) -> new StockPriceResponse(
                rs.getString("symb"), rs.getObject("bar_time", OffsetDateTime.class),
                rs.getBigDecimal("open"), rs.getBigDecimal("high"), rs.getBigDecimal("low"),
                rs.getBigDecimal("close"), rs.getBigDecimal("volume")));
    }
}
