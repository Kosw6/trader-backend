package com.example.trader.service;

import com.example.trader.entity.Note;
import com.example.trader.repository.NoteReporitory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NoteService {
    private final NoteReporitory noteReporitory;
    //노트저장
    public Long saveNote(Note note){
        return noteReporitory.save(note).getId();
    }
    //단일 노트건 가져오기
    public Note findNoteById(Long noteId){
        return noteReporitory.findById(noteId).orElseThrow(() -> {
            throw new RuntimeException();
        });
    }
    //유저아이디와 주식에 해당하는 모든 노트 가져오기
    public List<Note> findAllNoteByUserIdAndStock(Long userId, String stockName){
        return noteReporitory.findByUser_IdAndStockSymb(userId,stockName).orElseThrow(()->{throw new RuntimeException();});
    }
    //삭제
    public void deleteNote(Long noteId){
        noteReporitory.deleteById(noteId);
    }
    //수정
    public Long updateNote(Note note){
        return noteReporitory.save(note).getId();
    }

}
