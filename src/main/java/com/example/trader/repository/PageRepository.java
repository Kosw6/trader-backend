package com.example.trader.repository;

import com.example.trader.dto.map.ResponsePageDto;
import com.example.trader.entity.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page,Long> {
    List<Page> findAllByUserId(Long userId);
    List<Page> findAllByDirectoryTeamId(Long teamId);
    Optional<Page> findByIdAndUserId(Long pageId, Long userId);
    Optional<Page> findByIdAndDirectoryTeamId(Long pageId, Long teamId);
    @Query("""
        select new com.example.trader.dto.map.ResponsePageDto(p.id, p.title, d.id)
        from Page p join p.directory d
        where p.user.id = :userId
        order by p.id asc
    """)
    List<ResponsePageDto> findAllDtoByUserId(Long userId);

    @Query("""
        select new com.example.trader.dto.map.ResponsePageDto(p.id, p.title, d.id)
        from Page p join p.directory d
        where d.id = :dirId and p.user.id = :userId
        order by p.id asc
    """)
    List<ResponsePageDto> findAllDtoByDirectory(Long dirId, Long userId);

    @Query("""
        select p.id
        from Page p join p.directory d
        where d.id = :dirId and p.user.id = :userId
        order by p.id asc
    """)
    List<Long> findIdsByDirectory(Long dirId, Long userId);

    @Query(value = """
        WITH RECURSIVE sub AS (
            SELECT d.id
              FROM directory d
             WHERE d.id = :dirId AND d.user_id = :userId
            UNION ALL
            SELECT c.id
              FROM directory c
              JOIN sub s ON c.parent_id = s.id
             WHERE c.user_id = :userId
        )
        SELECT p.id
          FROM page p
          JOIN sub s ON p.directory_id = s.id
         WHERE p.user_id = :userId
         ORDER BY p.id
        """, nativeQuery = true)
    List<Long> findPageIdsInDirectoryTree(@Param("dirId") Long dirId, @Param("userId") Long userId);

    @Query(value = """
    WITH RECURSIVE sub AS (
        SELECT d.id
          FROM directory d
         WHERE d.id = :dirId
           AND d.team_id = :teamId
        UNION ALL
        SELECT c.id
          FROM directory c
          JOIN sub s ON c.parent_id = s.id
         WHERE c.team_id = :teamId
    )
    SELECT p.id
      FROM page p
      JOIN sub s ON p.directory_id = s.id
     ORDER BY p.id
    """, nativeQuery = true)
    List<Long> findPageIdsInDirectoryTreeInTeam(@Param("dirId") Long dirId,
                                                @Param("teamId") Long teamId);

    boolean existsByIdAndUserId(Long id, Long userId);

    Long deleteByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndDirectoryTeamId(Long id, Long teamId);

}
