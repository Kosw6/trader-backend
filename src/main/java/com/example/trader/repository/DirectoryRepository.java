package com.example.trader.repository;

import com.example.trader.dto.map.ResponseDirectoryDto;
import com.example.trader.entity.Directory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DirectoryRepository extends JpaRepository<Directory,Long> {
    // 사용자 소유 모든 디렉토리 (플랫)
    List<Directory> findAllByUserId(Long userId);

    // 루트 디렉토리만
    List<Directory> findAllByUserIdAndParentIsNull(Long userId);
    Optional<Directory> findByIdAndUserId(Long id, Long userId);
    void deleteByIdAndUserId(Long id, Long userId);

    @Query("""
        select new com.example.trader.dto.map.ResponseDirectoryDto(d.id, d.name, p.id)
        from Directory d
        left join d.parent p
        where d.user.id = :userId
        order by d.id asc
    """)
    List<ResponseDirectoryDto> findAllDtoByUserId(Long userId);

    @Query("""
        select new com.example.trader.dto.map.ResponseDirectoryDto(d.id, d.name, p.id)
        from Directory d
        left join d.parent p
        where d.id = :dirId and d.user.id = :userId
    """)
    Optional<ResponseDirectoryDto> findDtoByIdAndUserId(Long dirId, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

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
        SELECT id FROM sub ORDER BY id
        """, nativeQuery = true)
    List<Long> findDirectorySubtreeIds(@Param("dirId") Long dirId, @Param("userId") Long userId);

}
