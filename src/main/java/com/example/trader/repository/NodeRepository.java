package com.example.trader.repository;

import com.example.trader.entity.Node;
import com.example.trader.repository.projection.*;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NodeRepository extends JpaRepository<Node,Long> {
//    @QueryHints(@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_READ_ONLY, value = "true"))
//    Optional<Node> findById(Long id);

    //기본find 단건

    //fetch용 단건
    @Query("""
        select distinct n 
        from Node n
        left join fetch n.noteLinks l
        where n.id = :id
        """)
    Optional<Node> findByIdWithLinks(@Param("id") Long id);

    //Lazy로딩용 목록
    @Query("""
        select distinct n
        from Node n
        where n.page.id = :pageId
        order by n.id
        """)
    List<Node>  findAllByPageId(@Param("pageId")Long pageId);
    //fetch용 목록-노드기반
    @Query("""
    select distinct n
    from Node n
    left join fetch n.noteLinks l
    where n.page.id = :pageId
    order by n.id
    """)
    List<Node> findAllFetchByPageId(@Param("pageId") Long pageId);
    //20자 콘텐츠 뷰로 조회 no noteId,noteSubject
    @Query(value = """
      select
        id,
        x,
        y,
        subject,
        content_preview   as contentPreview,
        page_id           as pageId,
        created_date      as createdDate,
        modified_date     as modifiedDate
      from v_node_preview
      where page_id = :pageId
      order by id
      """, nativeQuery = true)
    List<NodePreviewProjection> findAllPreviewByPageId(@Param("pageId") Long pageId);

//    //20자 2단계 테이블
//    @Query("""
//          select
//              n.id                as id,
//              n.x                 as x,
//              n.y                 as y,
//              n.subject           as subject,
//              substring(n.content, 1, 20) as contentPreview,
//              n.symb              as symb,
//              n.recordDate        as recordDate,
//              n.page.id           as pageId,
//              n.createdDate       as createdDate,
//              n.modifiedDate      as modifiedDate,
//              l.note.id           as noteId,
//              no.subject          as noteSubject
//          from Node n
//          left join n.noteLinks l
//          left join l.note no
//          where n.page.id = :pageId
//          order by n.id
//        """)
//    List<NodePreviewWithNoteProjection> findAllPreviewWithNotesByPageId(Long pageId);


//    //500자 2단계 테이블
//    @Query("""
//      select
//          n.id                as id,
//          n.x                 as x,
//          n.y                 as y,
//          n.subject           as subject,
//          n.content           as contentPreview,
//          n.page.id           as pageId,
//          n.createdDate       as createdDate,
//          n.modifiedDate      as modifiedDate,
//          l.note.id           as noteId,
//          no.subject          as noteSubject
//      from Node n
//      left join n.noteLinks l
//      left join l.note no
//      where n.page.id = :pageId
//      order by n.id
//    """)
//    List<NodePreviewWithNoteProjection> findAllWithNotesByPageId(Long pageId);

//    json aggresive
//    @Query(value = """
//          select
//                n.id,
//                n.x,
//                n.y,
//                n.subject,
//                n.content,
//                n.symb,
//                n.record_date,
//                n.page_id,
//                n.created_date,
//                n.modified_date,
//                coalesce(
//                  jsonb_object_agg(l.note_id, no.subject)
//                    filter (where l.note_id is not null),
//                  '{}'::jsonb
//                ) as notes_json
//              from node n
//              left join node_note_link l on l.node_id = n.id
//              left join note no on no.id = l.note_id
//              where n.page_id = :pageId
//              group by n.id, n.x, n.y, n.subject, n.content, n.symb,
//                       n.record_date, n.page_id, n.created_date, n.modified_date
//              order by n.id
//        """, nativeQuery = true)
//    List<NodeRowProjection> findAllNodeRowProjectionByPageId(@Param("pageId") Long pageId);

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


    //2단계 조회 노드용
    @Query("""
      select
        n.id as id,
        n.x as x,
        n.y as y,
        n.subject as subject,
        substring(n.content, 1, 20) as contentPreview,
        n.symb as symb,
        n.recordDate as recordDate,
        n.modifiedDate as modifiedDate
      from Node n
      where n.page.id = :pageId
      order by n.id
    """)
    List<NodePreviewRow> findNode2StepByPageId(Long pageId);
    //2단계 조회 링크용
    @Query("""
      select
        l.node.id as nodeId,
        l.noteId as noteId,
        l.noteSubject as noteSubject
      from NodeNoteLink l
      where l.node.id in :nodeIds
      order by l.node.id, l.noteId
    """)
    List<LinkRow> findLinks2StepByNodeIds(Collection<Long> nodeIds);

}
