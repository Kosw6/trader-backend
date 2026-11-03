package com.example.trader.repository;

import com.example.trader.dto.PriceBarDto;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in, bounded SELECTs only. Never runs Spring schema initialization. */
@EnabledIfEnvironmentVariable(named = "PRICE_LIVE_READ_TEST", matches = "enabled")
class PriceBarLiveDatabaseReadTest {
    SingleConnectionDataSource ds;
    JdbcTemplate jdbc;
    PriceBarRepository repository;
    final OffsetDateTime cursor = OffsetDateTime.parse("2026-10-23T23:59:59Z");

    @BeforeEach void connect() {
        ds = new SingleConnectionDataSource(System.getenv("PRICE_AGG_TEST_URL"),
                System.getenv("PRICE_AGG_TEST_USER"), System.getenv("PRICE_AGG_TEST_PASSWORD"), true);
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("SET default_transaction_read_only = on");
        jdbc.execute("SET statement_timeout = '5s'");
        repository = new PriceBarRepository(new NamedParameterJdbcTemplate(ds));
    }

    @AfterEach void close() { if (ds != null) ds.destroy(); }

    @Test void readerIsAuthenticatedAndCannotAccessPrivateApplicationData() {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("trader_integrated_reader");
        assertThat(jdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
        assertThat(jdbc.queryForObject("SELECT has_schema_privilege(current_user,'application','USAGE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT has_schema_privilege(current_user,'ai','USAGE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
              WHERE n.nspname IN ('source_local','source_remote','price_incremental567')
                AND c.relkind IN ('r','p') AND has_schema_privilege(current_user,n.oid,'USAGE')
                AND has_table_privilege(current_user,c.oid,'INSERT'))
            """, Boolean.class)).isFalse();
    }

    @ParameterizedTest @ValueSource(longs = {13, 2872, 4106})
    void ordinaryRepositoryMatchesIndependentDailyAggregation(long assetId) {
        for (String interval : List.of("1D", "1W", "1M", "1Y")) {
            var actual = repository.findBefore(assetId, cursor, "KIS", interval, 3);
            assertThat(actual).hasSize(3);
            var start = actual.get(actual.size()-1).timestamp();
            var raw = jdbc.query("""
                SELECT bar_time AT TIME ZONE 'UTC' AS day,open,high,low,close,volume
                FROM public.price_bar WHERE asset_id=? AND source='KIS' AND "interval"='1D'
                  AND display_policy='FULL_OHLC' AND bar_time>=? AND bar_time<=? ORDER BY bar_time
                """, (rs, n) -> new Daily(rs.getObject("day", LocalDateTime.class).toLocalDate(),
                    rs.getBigDecimal("open"),rs.getBigDecimal("high"),rs.getBigDecimal("low"),
                    rs.getBigDecimal("close"),rs.getBigDecimal("volume")), assetId, start, cursor);
            verify(actual, raw, interval, assetId, false);
        }
    }

    @ParameterizedTest @ValueSource(longs = {13, 2872, 4106})
    void adjustedRepositoryUsesSelectedGenerationAndMatchesDailyRows(long assetId) {
        var snapshots = repository.chartSnapshots(assetId);
        var selected = snapshots.stream().filter(s -> s.defaultForChart()).findFirst().orElseThrow();
        assertThat(selected.basis()).isEqualTo("KIS_PROVIDER_ADJUSTED");
        assertThat(selected.coverageEnd()).isEqualTo(LocalDate.parse("2026-10-23"));
        for (String interval : List.of("1D", "1W", "1M", "1Y")) {
            var actual = repository.findSnapshotBefore(assetId, cursor, "KIS", interval, 3, selected.id());
            assertThat(actual).hasSize(3);
            var raw = jdbc.query("""
                SELECT trade_date,open,high,low,close,volume FROM public.price_chart_snapshot_bar
                WHERE snapshot_id=? AND trade_date>=? AND trade_date BETWEEN ? AND ? ORDER BY trade_date
                """, (rs,n) -> new Daily(rs.getObject("trade_date", LocalDate.class),
                    rs.getBigDecimal("open"),rs.getBigDecimal("high"),rs.getBigDecimal("low"),
                    rs.getBigDecimal("close"),rs.getBigDecimal("volume")), selected.id(),
                    actual.get(actual.size()-1).timestamp().toLocalDate(), selected.coverageStart(), selected.coverageEnd());
            verify(actual, raw, interval, assetId, true);
        }
        assertThatThrownBy(() -> repository.findSnapshotBefore(-1L, cursor,"KIS","1D",3,selected.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    record Daily(LocalDate day, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {}

    static LocalDate bucket(LocalDate day, String interval) {
        return switch (interval) {
            case "1D" -> day;
            case "1W" -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "1M" -> day.withDayOfMonth(1);
            case "1Y" -> day.withDayOfYear(1);
            default -> throw new IllegalArgumentException();
        };
    }

    static void verify(List<PriceBarDto> actual, List<Daily> raw, String interval, long assetId, boolean adjusted) {
        assertThat(raw).isNotEmpty();
        assertThat(raw.stream().map(Daily::day).distinct().count()).isEqualTo(raw.size());
        Map<LocalDate,List<Daily>> groups = new TreeMap<>(Comparator.reverseOrder());
        for (var row : raw) groups.computeIfAbsent(bucket(row.day(),interval), k -> new ArrayList<>()).add(row);
        assertThat(groups).hasSize(actual.size());
        int index=0;
        for (var entry : groups.entrySet()) {
            var got=actual.get(index++);
            var rows=entry.getValue();
            assertThat(got.assetId()).isEqualTo(assetId);
            assertThat(got.source()).isEqualTo("KIS");
            assertThat(got.interval()).isEqualTo(interval);
            assertThat(got.currency()).isEqualTo("USD");
            assertThat(got.timestamp()).isEqualTo(entry.getKey().atStartOfDay().atOffset(ZoneOffset.UTC));
            assertThat(got.open()).isEqualByComparingTo(rows.get(0).open());
            assertThat(got.close()).isEqualByComparingTo(rows.get(rows.size()-1).close());
            assertThat(got.high()).isEqualByComparingTo(rows.stream().map(Daily::high).max(BigDecimal::compareTo).orElseThrow());
            assertThat(got.low()).isEqualByComparingTo(rows.stream().map(Daily::low).min(BigDecimal::compareTo).orElseThrow());
            if (rows.stream().anyMatch(r -> r.volume()==null)) assertThat(got.volume()).isNull();
            else assertThat(got.volume()).isEqualByComparingTo(rows.stream().map(Daily::volume).reduce(BigDecimal.ZERO, BigDecimal::add));
            if (adjusted) assertThat(got.adjustedClose()).isEqualByComparingTo(got.close());
        }
    }
}
