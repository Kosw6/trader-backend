package com.example.trader.service;

import com.example.trader.dto.ResponseNoteDto;
import com.example.trader.entity.Note;
import com.example.trader.entity.User;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.NoteRepository;
import com.example.trader.support.fixtures.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    NoteRepository noteRepository;

    @InjectMocks
    NoteService noteService;

    // ── saveNote ──────────────────────────────────

    @Test
    void 노트를_저장하면_저장된_노트의_ID가_반환된다() {
        // given
        User user = TestFixtures.userWithId();
        Note note = TestFixtures.note(user, null);

        Note saved = TestFixtures.note(user, null);
        ReflectionTestUtils.setField(saved, "id", 1L); // DB가 채워줄 ID를 수동으로 세팅

        given(noteRepository.save(any(Note.class))).willReturn(saved);

        // when
        Long result = noteService.saveNote(note);

        // then
        assertThat(result).isEqualTo(1L);
    }

    // ── findNoteById ──────────────────────────────

    @Test
    void 존재하는_노트ID로_조회하면_노트가_반환된다() {
        // given
        User user = TestFixtures.userWithId();
        Note saved = TestFixtures.note(user, null);

        ReflectionTestUtils.setField(saved, "id", 1L);

        given(noteRepository.findByIdAndUserId(1L, user.getId()))
                .willReturn(Optional.of(saved));

        // when
        Note result = noteService.findNoteById(user.getId(), 1L);

        // then
        assertThat(result).isEqualTo(saved);

    }

    @Test
    void 존재하지_않는_노트ID로_조회하면_BaseException이_발생한다() {
        // given
        Long userId = 1L;
        Long noteId = 999L;

        given(noteRepository.findByIdAndUserId(noteId, userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                noteService.findNoteById(userId, noteId))
                .isInstanceOf(BaseException.class)
                .hasMessage(BaseResponseStatus.INVALID_NOTE.getMessage());
    }

    // ── findAllNoteByUserIdAndStock ────────────────

    @Test
    void 해당_주식의_노트가_있으면_페이지가_반환된다() {
        // given
        Long userId = 1L;
        String stockName = "AAPL";
        Pageable pageable = PageRequest.of(0, 10);

        User user = TestFixtures.userWithId();
        Note saved = TestFixtures.note(user, null);

        Page<Note> notePage = new PageImpl<>(
                List.of(saved),
                pageable,
                1
        );

        given(noteRepository.findByUserIdAndStockSymb(userId, stockName, pageable))
                .willReturn(notePage);

        // when
        Page<ResponseNoteDto> result =
                noteService.findAllNoteByUserIdAndStock(userId, stockName, pageable);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);

        then(noteRepository)
                .should()
                .findByUserIdAndStockSymb(userId, stockName, pageable);
    }

    @Test
    void 해당_주식의_노트가_없으면_BaseException이_발생한다() {
        // given
        Long userId = 1L;
        String stockName = "AAPL";
        Pageable pageable = PageRequest.of(0, 10);

        Page<Note> emptyPage = Page.empty(pageable);

        given(noteRepository.findByUserIdAndStockSymb(userId, stockName, pageable))
                .willReturn(emptyPage);

        // when & then
        assertThatThrownBy(() ->
                noteService.findAllNoteByUserIdAndStock(userId, stockName, pageable)
        ).isInstanceOf(BaseException.class);

        then(noteRepository)
                .should()
                .findByUserIdAndStockSymb(userId, stockName, pageable);
    }

    // ── findAllNoteRangeByUserIdAndStock ──────────

    @Test
    void 날짜_범위_내_노트가_있으면_페이지가_반환된다() {
        // given
        Long userId = 1L;
        String stockName = "AAPL";
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        User user = TestFixtures.userWithId();
        Note saved = TestFixtures.note(user, null);

        Page<Note> notePage = new PageImpl<>(List.of(saved), pageable, 1);

        given(noteRepository.findByUserIdAndStockSymbAndNoteDateBetween(
                userId, stockName, startDate, endDate, pageable
        )).willReturn(notePage);

        // when
        Page<ResponseNoteDto> result =
                noteService.findAllNoteRangeByUserIdAndStock(
                        userId, stockName, pageable, startDate, endDate
                );

        // then
        assertThat(result).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
        then(noteRepository).should()
                .findByUserIdAndStockSymbAndNoteDateBetween(
                        userId, stockName, startDate, endDate, pageable
                );
    }

    @Test
    void 날짜_범위_내_노트가_없으면_BaseException이_발생한다() {
        // given
        Long userId = 1L;
        String stockName = "AAPL";
        Pageable pageable = PageRequest.of(0, 10);
        LocalDate startDate = LocalDate.of(2024, 1, 1);
        LocalDate endDate = LocalDate.of(2024, 1, 31);

        given(noteRepository.findByUserIdAndStockSymbAndNoteDateBetween(
                userId, stockName, startDate, endDate, pageable
        )).willReturn(Page.empty(pageable));

        // when & then
        assertThatThrownBy(() ->
                noteService.findAllNoteRangeByUserIdAndStock(
                        userId, stockName, pageable, startDate, endDate
                )
        ).isInstanceOf(BaseException.class);
    }



    // ── findAllNoteByUser ─────────────────────────

    @Test
    void 유저의_전체_노트를_조회하면_페이지가_반환된다() {
        // given
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 10);

        User user = TestFixtures.userWithId();
        Note saved = TestFixtures.note(user, null);

        Page<Note> notePage = new PageImpl<>(List.of(saved), pageable, 1);

        given(noteRepository.findByUserId(userId, pageable))
                .willReturn(notePage);

        // when
        Page<ResponseNoteDto> result =
                noteService.findAllNoteByUser(userId, pageable);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);

        then(noteRepository).should()
                .findByUserId(userId, pageable);
    }





    // ── deleteNote ────────────────────────────────

    @Test
    void 노트를_삭제하면_repository의_delete가_호출된다() {
        // given
        Long noteId = 1L;

        // when
        noteService.deleteNote(noteId);

        // then
        then(noteRepository).should()
                .deleteById(noteId);
    }

    // ── updateNote ────────────────────────────────

    @Test
    void 노트를_수정하면_저장_후_ID가_반환된다() {
        // given
        User user = TestFixtures.userWithId();
        Note note = TestFixtures.note(user, null);
        ReflectionTestUtils.setField(note, "id", 1L);

        given(noteRepository.save(note))
                .willReturn(note);

        // when
        Long result = noteService.updateNote(note);

        // then
        assertThat(result).isEqualTo(1L);
        then(noteRepository).should()
                .save(note);
    }
}
