package com.example.trader.repository;

import com.example.trader.entity.Ticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TickerRepository extends JpaRepository<Ticker,Long> {
    /**
     * 검색 후보는 레거시 stock_info에서 찾되 KIS provider identity를 통해
     * 신규 asset_id로 연결한다. Alias는 표시 이름 보강에만 사용한다.
     */
    @Query(value = """
        SELECT DISTINCT
            a.id AS asset_id,
            a.ticker,
            COALESCE(alias_names.korean_name, NULLIF(BTRIM(si.name), ''), a.name) AS display_name,
            COALESCE(alias_names.english_name, NULLIF(BTRIM(si.ename), '')) AS english_name,
            a.exchange,
            a.currency,
            a.asset_type,
            CASE
                WHEN si.symb ILIKE :q || '%' THEN 0
                WHEN si.name ILIKE :q || '%' OR si.ename ILIKE :q || '%' THEN 1
                ELSE 2
            END AS match_rank,
            GREATEST(
                similarity(si.symb, :q),
                similarity(COALESCE(si.name, ''), :q),
                similarity(COALESCE(si.ename, ''), :q)
            ) AS match_score
        FROM stock_info si
        JOIN asset_provider_symbol aps
          ON aps.provider = 'KIS'
         AND aps.provider_symbol = UPPER(BTRIM(si.symb))
         AND aps.provider_exchange_code = UPPER(BTRIM(si.excd))
         AND aps.is_current = TRUE
        JOIN asset a
          ON a.id = aps.asset_id
         AND a.active = TRUE
        LEFT JOIN LATERAL (
            SELECT
                MAX(aa.alias) FILTER (WHERE aa.alias_type = 'KOREAN_NAME') AS korean_name,
                MAX(aa.alias) FILTER (WHERE aa.alias_type = 'ENGLISH_NAME') AS english_name
            FROM asset_alias aa
            WHERE aa.asset_id = a.id
        ) alias_names ON TRUE
        WHERE si.symb ILIKE :q || '%'
           OR si.name ILIKE :q || '%'
           OR si.ename ILIKE :q || '%'
           OR similarity(si.symb, :q) > 0.35
           OR similarity(COALESCE(si.name, ''), :q) > 0.35
           OR similarity(COALESCE(si.ename, ''), :q) > 0.35
        ORDER BY match_rank, match_score DESC, ticker
        LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> suggestFuzzy(@Param("q") String q, @Param("limit") int limit);
}
