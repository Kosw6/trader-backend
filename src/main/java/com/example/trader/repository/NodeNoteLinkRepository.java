package com.example.trader.repository;

import com.example.trader.entity.NodeNoteLink;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface NodeNoteLinkRepository extends JpaRepository<NodeNoteLink, Long> {
    // 부모 묶음으로 링크를 한 번에
    @Query("""
      select l
      from NodeNoteLink l
      where l.node.id in :nodeIds
      order by l.node.id, l.id
    """)
    List<NodeNoteLink> findAllByNodeIdIn(@Param("nodeIds") Collection<Long> nodeIds);

//    // (선택) 더 가벼운 프로젝션(엔티티 대신 튜플)
//    @Query("""
//      select l.node.id as nodeId, l.noteId as noteId
//      from NodeNoteLink l
//      where l.node.id in :nodeIds
//      order by l.node.id, l.id
//    """)
//    List<NodeNoteIdPair> findPairsByNodeIdIn(@Param("nodeIds") Collection<Long> nodeIds);

    @Query("""
    select l
    from NodeNoteLink l
    where l.node.id = :nodeId
    order by l.id
    """)
    List<NodeNoteLink> findAllByNodeId(@Param("nodeId") Long nodeId);

//    //nodeId만 가져오기
//    @Query("""
//    select l.node.id as nodeId, l.noteId as noteId
//    from NodeNoteLink l
//    where l.node.id = :nodeId
//    order by l.id
//    """)
//    List<NodeNoteIdPair> findPairsByNodeId(@Param("nodeId") Long nodeId);

    interface NodeNoteIdPair {
        Long getNodeId();
        Long getNoteId();
    }
}
