package com.example.trader.repository;

import com.example.trader.entity.Ticker;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TickerRepository extends JpaRepository<Ticker,Long> {
//    // (1) 접두사 자동완성: btree(prefix) 인덱스 기대 => Index Scan
//    @Query("""
//        select t from Ticker t
//        where t.symbol like concat(:q, '%')
//           or t.nameKo  like concat(:q, '%')
//        order by
//            case when t.symbol like concat(:q, '%') or t.nameKo like concat(:q, '%') then 0 else 1 end,
//            t.symbol asc
//        """)
//    List<Ticker> suggestPrefix(@Param("q") String q, Pageable pageable);

    // (2) 부분일치/오타 허용: pg_trgm + GIN 기대
    //  → similarity()는 JPQL에 없으므로 nativeQuery로
    @Query(value = """
        set local pg_trgm.similarity_threshold = 0.35;
        select symb, name, ename
        from ticker
        where symb ilike :q || '%'
           or name ilike :q || '%'
           or similarity(symb, :q) > 0.35
           or similarity(name, :q) > 0.35
        order by
          case when symb ilike :q || '%' or name ilike :q || '%' then 0 else 1 end,
          similarity(name, :q) desc,
          length(name) asc
        limit :limit
        """, nativeQuery = true)
    List<Object[]> suggestFuzzy(@Param("q") String q, @Param("limit") int limit);
}
