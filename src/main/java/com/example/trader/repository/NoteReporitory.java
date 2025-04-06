package com.example.trader.repository;

import com.example.trader.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteReporitory extends JpaRepository<Note,Long> {
    Optional<List<Note>> findByUser_IdAndStockSymb(Long userID, String stockName);
}
