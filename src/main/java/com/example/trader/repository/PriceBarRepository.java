package com.example.trader.repository;

import com.example.trader.dto.PriceBarDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PriceBarRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public List<PriceBarDto> findBefore(Long assetId,
                                        OffsetDateTime cursor,
                                        String source,
                                        String interval,
                                        int limit) {
        String sql = """
                SELECT
                    asset_id,
                    bar_time,
                    open,
                    high,
                    low,
                    close,
                    adjusted_close,
                    volume,
                    currency,
                    source,
                    "interval"
                FROM price_bar
                WHERE asset_id = :assetId
                  AND source = :source
                  AND "interval" = :interval
                  AND bar_time <= :cursor
                  AND display_policy = 'FULL_OHLC'
                ORDER BY bar_time DESC
                LIMIT :limit
                """;

        var params = new MapSqlParameterSource()
                .addValue("assetId", assetId)
                .addValue("cursor", cursor)
                .addValue("source", source)
                .addValue("interval", interval)
                .addValue("limit", limit);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> new PriceBarDto(
                rs.getLong("asset_id"),
                rs.getObject("bar_time", OffsetDateTime.class),
                rs.getBigDecimal("open"),
                rs.getBigDecimal("high"),
                rs.getBigDecimal("low"),
                rs.getBigDecimal("close"),
                rs.getBigDecimal("adjusted_close"),
                rs.getBigDecimal("volume"),
                rs.getString("currency"),
                rs.getString("source"),
                rs.getString("interval")
        ));
    }
}
