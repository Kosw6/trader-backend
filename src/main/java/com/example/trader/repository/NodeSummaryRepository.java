package com.example.trader.repository;

import com.example.trader.entity.NodeSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NodeSummaryRepository extends JpaRepository<NodeSummary, Long> {

    @Query("""
    select distinct n
    from NodeSummary n
    left join fetch n.noteLinks
    where n.pageId = :pageId
    order by n.id
    """)
    List<NodeSummary> findAllFetchSummaryByPageId(Long pageId);
}
