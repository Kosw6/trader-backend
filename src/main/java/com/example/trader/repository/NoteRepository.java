package com.example.trader.repository;

import com.example.trader.entity.Note;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface NoteRepository extends JpaRepository<Note,Long> {
    @Query("select n from Note n where n.user.id = :userId and (n.stockSymb = :stockName or :stockName member of n.stockSymbs)")
    Page<Note> findByUserIdAndStockSymb(@Param("userId") Long userId, @Param("stockName") String stockName, Pageable pageable);
    Page<Note> findByUserId(Long userId, Pageable pageable);
    @Query("select n from Note n where n.user.id = :userId and (n.stockSymb = :stockSymb or :stockSymb member of n.stockSymbs) and n.noteDate between :startDate and :endDate")
    Page<Note> findByUserIdAndStockSymbAndNoteDateBetween(@Param("userId") Long userId,
                                                          @Param("stockSymb") String stockSymb,
                                                          @Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate,
                                                          Pageable pageable);

    @Query("select n.id from Note n where n.user.id = :userId and n.id in :ids")
    List<Long> findIdsByUserIdAndIdIn(@Param("userId") Long userId, @Param("ids") Set<Long> ids);
    Optional<Note> findByIdAndUserId(Long id, Long userId);
}
