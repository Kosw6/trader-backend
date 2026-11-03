package com.example.trader.repository;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.*;

/** 명시적으로 지정한 TimescaleDB 연결의 임시 테이블만 사용한다. 공개 가격 원본은 변경하지 않는다. */
@EnabledIfEnvironmentVariable(named="PRICE_AGG_TEST_URL", matches=".+")
class PriceBarAggregationTest {
    SingleConnectionDataSource ds;
    JdbcTemplate jdbc;
    PriceBarRepository repository;
    @BeforeEach void setup() {
        ds = new SingleConnectionDataSource(System.getenv("PRICE_AGG_TEST_URL"),
                System.getenv("PRICE_AGG_TEST_USER"), System.getenv("PRICE_AGG_TEST_PASSWORD"), true);
        jdbc = new JdbcTemplate(ds);
        repository = new PriceBarRepository(new NamedParameterJdbcTemplate(ds));
        // Reader roles can default to read-only. Permit DDL only in this test transaction;
        // every mutation below names pg_temp and the entire transaction is rolled back.
        jdbc.execute("BEGIN READ WRITE");
        jdbc.execute("SET LOCAL statement_timeout = '5s'");
        jdbc.execute("SET LOCAL search_path = pg_temp, public");
        jdbc.execute("CREATE TEMP TABLE price_bar (LIKE public.price_bar INCLUDING DEFAULTS)");
        jdbc.execute("ALTER TABLE pg_temp.price_bar ALTER COLUMN id SET DEFAULT 0");
        jdbc.execute("CREATE TEMP TABLE price_bar_weekly AS SELECT * FROM public.price_bar_weekly WITH NO DATA");
        jdbc.execute("CREATE TEMP TABLE price_chart_snapshot (LIKE public.price_chart_snapshot INCLUDING DEFAULTS)");
        jdbc.execute("CREATE TEMP TABLE price_chart_snapshot_bar (LIKE public.price_chart_snapshot_bar INCLUDING DEFAULTS)");
        jdbc.execute("""
            INSERT INTO pg_temp.price_chart_snapshot(id,asset_id,source_object_id,source,currency,basis,volume_basis,
            coverage_start,coverage_end,collected_at,row_count,content_hash)
            VALUES (7,1,1,'KIS','USD','KIS_PROVIDER_ADJUSTED','PROVIDER_REPORTED','2024-06-01','2024-06-30',now(),2,repeat('a',64))
            """);
        jdbc.execute("""
            INSERT INTO pg_temp.price_chart_snapshot_bar VALUES
            (7,'2024-06-07',1,119,122,117,120,100),(7,'2024-06-10',2,120,124,118,123,200)
            """);
        jdbc.execute("""
            INSERT INTO pg_temp.price_bar(asset_id,source,"interval",bar_time,open,high,low,close,volume,currency,collected_at,display_policy)
            VALUES (1,'KIS','1D','2025-12-29Z',10,12,9,11,100,'USD',now(),'FULL_OHLC'),
                   (1,'KIS','1D','2025-12-31Z',20,25,18,22,200,'USD',now(),'FULL_OHLC'),
                   (1,'KIS','1D','2026-01-02Z',30,35,28,33,300,'USD',now(),'FULL_OHLC'),
                   (1,'KIS','1D','2026-01-05Z',40,45,38,44,400,'USD',now(),'FULL_OHLC'),
                   (1,'OTHER','1D','2026-01-05Z',999,999,999,999,999,'USD',now(),'FULL_OHLC'),
                   (1,'KIS','1W','2026-01-05Z',999,999,999,999,999,'USD',now(),'FULL_OHLC'),
                   (1,'KIS','1D','2026-01-06Z',999,999,999,999,999,'USD',now(),'HIDDEN')
            """);
    }
    @AfterEach void close() {
        if (ds != null) {
            try { if (jdbc != null) jdbc.execute("ROLLBACK"); }
            finally { ds.destroy(); }
        }
    }
    @Test void calendarMonthAndYearDoNotLeakCrossBoundaryWeek() {
        for (String interval : new String[]{"1M","1Y"}) {
            var rows = repository.findBefore(1L, OffsetDateTime.parse("2026-02-01T00:00:00Z"),"KIS",interval,10);
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).open()).isEqualByComparingTo("30");
            assertThat(rows.get(0).close()).isEqualByComparingTo("44");
            assertThat(rows.get(0).volume()).isEqualByComparingTo("700");
            assertThat(rows.get(1).open()).isEqualByComparingTo("10");
            assertThat(rows.get(1).close()).isEqualByComparingTo("22");
            assertThat(rows.get(1).volume()).isEqualByComparingTo("300");
            assertThat(rows.get(0).adjustedClose()).isNull();
            assertThat(rows.get(0).interval()).isEqualTo(interval);
        }
    }
    @Test void weeklyCursorExcludesLaterDaysAndReadsEarlierCagg() {
        jdbc.execute("INSERT INTO pg_temp.price_bar_weekly VALUES (1,'KIS','USD','2025-12-22Z',1,3,1,2,NULL,50)");
        var rows=repository.findBefore(1L,OffsetDateTime.parse("2025-12-30T00:00:00Z"),"KIS","1W",10);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).close()).isEqualByComparingTo("11");
        assertThat(rows.get(0).volume()).isEqualByComparingTo("100");
        assertThat(rows.get(1).close()).isEqualByComparingTo("2");
    }
    @Test void cutoffAndLimitAreAppliedBeforeGroupingAndAfterGroupingRespectively() {
        var rows=repository.findBefore(1L,OffsetDateTime.parse("2026-01-03T00:00:00Z"),"KIS","1M",1);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).close()).isEqualByComparingTo("33");
        assertThat(rows.get(0).volume()).isEqualByComparingTo("300");
    }
    @Test void adjustedSnapshotDoesNotReadRawOrMixPriceGenerations() {
        var rows=repository.findSnapshotBefore(1L,OffsetDateTime.parse("2026-01-01T00:00:00Z"),"KIS","1M",10,7L);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).open()).isEqualByComparingTo("119");
        assertThat(rows.get(0).close()).isEqualByComparingTo("123");
        assertThat(rows.get(0).volume()).isEqualByComparingTo("300");
        assertThat(rows.get(0).timestamp()).isEqualTo(OffsetDateTime.parse("2024-06-01T00:00:00Z"));
        assertThat(repository.findSnapshotBefore(1L,OffsetDateTime.parse("2024-06-01T00:00:00Z"),"KIS","1D",10,7L)).isEmpty();
    }
    @Test void snapshotCannotBeUsedForOtherAssetOrUnknownId() {
        assertThatThrownBy(()->repository.findSnapshotBefore(2L,OffsetDateTime.now(),"KIS","1D",10,7L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->repository.findSnapshotBefore(1L,OffsetDateTime.now(),"KIS","1D",10,99L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->repository.findSnapshotBefore(1L,OffsetDateTime.now(),"OTHER","1D",10,7L)).isInstanceOf(IllegalArgumentException.class);
    }
}
