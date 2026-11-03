package com.example.trader.repository;

import com.example.trader.dto.PriceBarDto;
import com.example.trader.dto.PriceChartSnapshotDto;
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

    public List<com.example.trader.dto.ChartCorporateActionDto> corporateActions(Long assetId) {
        return jdbcTemplate.query("SELECT * FROM price_chart_corporate_action WHERE asset_id=:assetId ORDER BY trading_date,id",
            new MapSqlParameterSource("assetId",assetId), (rs,n) -> new com.example.trader.dto.ChartCorporateActionDto(
                rs.getLong("id"),rs.getObject("trading_date",java.time.LocalDate.class),
                rs.getString("action_type"),rs.getInt("new_shares"),rs.getInt("old_shares"),rs.getString("source_url")));
    }

    public List<PriceChartSnapshotDto> chartSnapshots(Long assetId) {
        return jdbcTemplate.query("SELECT * FROM price_chart_snapshot WHERE asset_id=:assetId ORDER BY published_at DESC, id DESC",
            new MapSqlParameterSource("assetId",assetId), (rs,n) -> new PriceChartSnapshotDto(
                rs.getLong("id"),rs.getLong("asset_id"),rs.getString("source"),rs.getString("currency"),
                rs.getString("basis"),rs.getString("volume_basis"),rs.getObject("coverage_start",java.time.LocalDate.class),
                rs.getObject("coverage_end",java.time.LocalDate.class),rs.getObject("collected_at",OffsetDateTime.class),rs.getInt("row_count"),rs.getBoolean("default_for_chart")));
    }

    public List<PriceBarDto> findSnapshotBefore(Long assetId, OffsetDateTime cursor, String source,
                                               String interval, int limit, Long snapshotId) {
        if (chartSnapshots(assetId).stream().noneMatch(s -> s.id().equals(snapshotId) && s.source().equals(source))) {
            throw new IllegalArgumentException("해당 종목의 수정가격 검토본을 찾을 수 없습니다.");
        }
        String unit = switch(interval) {
            case "1D" -> "day"; case "1W" -> "week"; case "1M" -> "month"; case "1Y" -> "year";
            default -> throw new IllegalArgumentException("지원하지 않는 봉 주기입니다.");
        };
        String sql = """
            SELECT s.asset_id, s.source, s.currency,
                date_trunc('%s',b.trade_date::timestamp) AT TIME ZONE 'UTC' AS bar_time,
                first(b.open,b.trade_date) AS open,max(b.high) AS high,min(b.low) AS low,
                last(b.close,b.trade_date) AS close,
                CASE WHEN count(b.volume)=count(*) THEN sum(b.volume) END AS volume
            FROM price_chart_snapshot_bar b JOIN price_chart_snapshot s ON s.id=b.snapshot_id
            WHERE s.id=:snapshotId AND s.asset_id=:assetId AND s.source=:source
              AND b.trade_date::timestamp AT TIME ZONE 'UTC' <= :cursor
              AND b.trade_date BETWEEN s.coverage_start AND s.coverage_end
            GROUP BY s.asset_id,s.source,s.currency,date_trunc('%s',b.trade_date::timestamp)
            ORDER BY bar_time DESC LIMIT :limit
            """.formatted(unit,unit);
        var p=new MapSqlParameterSource("assetId",assetId).addValue("snapshotId",snapshotId)
            .addValue("source",source).addValue("cursor",cursor).addValue("limit",limit);
        return jdbcTemplate.query(sql,p,(rs,n)->new PriceBarDto(rs.getLong("asset_id"),
            rs.getObject("bar_time",OffsetDateTime.class),rs.getBigDecimal("open"),rs.getBigDecimal("high"),
            rs.getBigDecimal("low"),rs.getBigDecimal("close"),rs.getBigDecimal("close"),rs.getBigDecimal("volume"),
            rs.getString("currency"),rs.getString("source"),interval));
    }

    public List<PriceBarDto> findBefore(Long assetId,
                                        OffsetDateTime cursor,
                                        String source,
                                        String interval,
                                        int limit) {
        String sql = interval.equals("1D") ? """
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
                """ : aggregateSql(interval);

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

    // SQL 토큰은 사용자 문자열을 연결하지 않고 서버의 허용 목록에서만 고른다.
    static String aggregateSql(String interval) {
        String unit = switch (interval) {
            case "1W" -> "week";
            case "1M" -> "month";
            case "1Y" -> "year";
            default -> throw new IllegalArgumentException("지원하지 않는 집계 주기입니다.");
        };
        String raw = """
                SELECT asset_id, source, currency,
                       date_trunc('%s', bar_time AT TIME ZONE 'UTC') AT TIME ZONE 'UTC' AS bar_time,
                       first(open, bar_time) AS open, max(high) AS high, min(low) AS low,
                       last(close, bar_time) AS close,
                       CASE WHEN count(adjusted_close) = count(*) THEN last(adjusted_close, bar_time) END AS adjusted_close,
                       CASE WHEN count(volume) = count(*) THEN sum(volume) END AS volume
                FROM price_bar
                WHERE asset_id = :assetId AND source = :source AND "interval" = '1D'
                  AND display_policy = 'FULL_OHLC' AND bar_time <= :cursor
                %s
                GROUP BY asset_id, source, currency,
                         date_trunc('%s', bar_time AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'
                """.formatted(unit, interval.equals("1W") ?
                    "AND bar_time >= date_trunc('week', CAST(:cursor AS timestamptz) AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'" : "", unit);
        // 과거 커서가 주 중간이면 CAGG의 완성된 주봉을 그대로 반환하지 않는다.
        // 앞선 주는 CAGG, 커서가 속한 주는 커서까지의 일봉으로 계산한다.
        String rows = interval.equals("1W") ? """
                SELECT asset_id, source, currency, bar_time, open, high, low, close, adjusted_close, volume
                FROM price_bar_weekly
                WHERE asset_id = :assetId AND source = :source
                  AND bar_time < date_trunc('week', CAST(:cursor AS timestamptz) AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'
                UNION ALL
                """ + raw : raw;
        return "SELECT *, :interval AS interval FROM (" + rows + ") bars ORDER BY bar_time DESC LIMIT :limit";
    }
}
