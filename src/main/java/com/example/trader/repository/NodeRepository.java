package com.example.trader.repository;

import com.example.trader.entity.Node;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NodeRepository extends JpaRepository<Node,Long> {
    // NodeRepository.java
    List<Node> findByPageId(Long pageId);

    @Query("""
        select distinct n 
        from Node n
        left join fetch n.noteLinks l
        left join fetch l.note
        where n.id = :id
        """)
    Optional<Node> findByIdWithLinks(@Param("id") Long id);
    // NodeRepository.java
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
   update Node n
      set n.x = :x, n.y = :y
    where n.id = :nodeId
   """)
    int updatePosition(@Param("nodeId") Long nodeId,
                       @Param("x") double x,
                       @Param("y") double y);

}
