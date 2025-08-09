package com.example.trader.repository;

import com.example.trader.entity.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoteRepository extends JpaRepository<Note,Long> {
    Page<Note> findByUserIdAndStockSymb(Long userId, String stockName, Pageable pageable);
    Page<Note> findByUserId(Long userId, Pageable pageable);
}
