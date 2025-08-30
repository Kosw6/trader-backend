package com.example.trader.service;

import com.example.trader.dto.ResponseNoteDto;
import com.example.trader.entity.Note;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class NoteService {
    private final NoteRepository noteRepository;
    //노트저장
    public Long saveNote(Note note){
        return noteRepository.save(note).getId();
    }
    //단일 노트건 가져오기
    public Note findNoteById(Long noteId){
        return noteRepository.findById(noteId).orElseThrow(() -> {
            throw new BaseException(BaseResponseStatus.INVALID_NOTE);
        });
    }
    //유저아이디와 주식에 해당하는 모든 노트 가져오기
    public Page findAllNoteByUserIdAndStock(Long userId, String stockName, Pageable pageable){
        Page<Note> page = noteRepository.findByUserIdAndStockSymb(userId,stockName,pageable);
        if (page.isEmpty()) {
            throw new BaseException(BaseResponseStatus.INVALID_NOTE);
        }
        return page.map(ResponseNoteDto::fromEntity);
    }
    //유저아이디와 주식에 해당하는 모든 노트 가져오기
    public Page findAllNoteRangeByUserIdAndStock(Long userId, String stockName, Pageable pageable, LocalDate startDate, LocalDate endDate){
        Page<Note> page = noteRepository.findByUserIdAndStockSymbAndNoteDateBetween(userId,stockName,startDate,endDate,pageable);
        if (page.isEmpty()) {
            throw new BaseException(BaseResponseStatus.INVALID_NOTE);
        }
        return page.map(ResponseNoteDto::fromEntity);
    }
    public Page findAllNoteByUser(Long userId, Pageable pageable){
        Page<Note> page = noteRepository.findByUserId(userId, pageable);
        // 페이지 내용이 비었을 때 예외 처리
        if (page.isEmpty()) {
            throw new BaseException(BaseResponseStatus.INVALID_NOTE);
        }
        return page.map(ResponseNoteDto::fromEntity);
    }
    //삭제
    public void deleteNote(Long noteId){
        noteRepository.deleteById(noteId);
    }
    //수정
    public Long updateNote(Note note){
        return noteRepository.save(note).getId();
    }

}
