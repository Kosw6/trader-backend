package com.example.trader.repository;

import com.example.trader.entity.Note;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NoteRepository extends JpaRepository<Note,Long> {
    Page<Note> findByUserIdAndStockSymb(Long userId, String stockName, Pageable pageable);
    Page<Note> findByUserId(Long userId, Pageable pageable);
    Page<Note> findByUserIdAndStockSymbAndNoteDateBetween(Long userId,
                                                          String stockSymb,
                                                          LocalDate startDate,   // 포함(inclusive)
                                                          LocalDate endDate,     // 포함(inclusive)
                                                          Pageable pageable);

    @Query("select n.id from Note n where n.user.id = :userId and n.id in :ids")
    List<Long> findIdsByUserIdAndIdIn(@Param("userId") Long userId, @Param("ids") Set<Long> ids);
}
