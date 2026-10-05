package com.example.trader.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PriceBarQueryRepositoryTest {
    JdbcTemplate jdbc;
    PriceBarQueryRepository repository;
    OffsetDateTime day = OffsetDateTime.parse("2024-01-02T00:00:00Z");

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL", "sa", "");
        jdbc = new JdbcTemplate(ds);
        repository = new PriceBarQueryRepository(new NamedParameterJdbcTemplate(ds));
        // stock 테이블 자체가 없는 환경에서 조회가 성공해야 한다.
        jdbc.execute("CREATE TABLE asset_provider_symbol(asset_id BIGINT, provider VARCHAR, provider_symbol VARCHAR, is_current BOOLEAN)");
        jdbc.execute("CREATE TABLE price_bar(asset_id BIGINT, source VARCHAR, \"interval\" VARCHAR, bar_time TIMESTAMP WITH TIME ZONE, open NUMERIC, high NUMERIC, low NUMERIC, close NUMERIC, volume NUMERIC)");
        jdbc.execute("INSERT INTO asset_provider_symbol VALUES(1,'KIS','NVDA',true),(2,'KIS','OLD',false)");
        for (int i = 0; i < 3; i++) jdbc.update("INSERT INTO price_bar VALUES(1,'KIS','1D',?,10,12,9,11,100)", day.plusDays(i));
        jdbc.update("INSERT INTO price_bar VALUES(1,'OTHER','1D',?,99,99,99,99,1),(1,'KIS','1W',?,88,88,88,88,1)", day, day);
    }

    @Test void filtersProviderIntervalAndKeepsInclusiveOrder() {
        var rows = repository.between(" nvda ", day, day.plusDays(1));
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).symb()).isEqualTo("NVDA");
        assertThat(rows.get(0).close()).isEqualByComparingTo("11");
        assertThat(rows.get(1).timestamp()).isEqualTo(day.plusDays(1));
    }

    @Test void cursorDirectionLimitAndEmptyRange() {
        assertThat(repository.before("NVDA", day.plusDays(1), 1).get(0).timestamp()).isEqualTo(day.plusDays(1));
        assertThat(repository.after("NVDA", day, 2)).hasSize(2);
        assertThat(repository.after("NVDA", day.plusDays(10), 2)).isEmpty();
    }

    @Test void rejectsAmbiguousMissingAndStaleIdentity() {
        assertThatThrownBy(() -> repository.after("OLD", day, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.after("UNKNOWN", day, 1)).isInstanceOf(IllegalArgumentException.class);
        jdbc.execute("INSERT INTO asset_provider_symbol VALUES(3,'KIS','NVDA',true)");
        assertThatThrownBy(() -> repository.after("NVDA", day, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
