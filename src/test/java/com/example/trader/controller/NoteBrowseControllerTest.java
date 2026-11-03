package com.example.trader.controller;

import com.example.trader.dto.RequestNoteDto;
import com.example.trader.dto.ResponseUserDto;
import com.example.trader.entity.Note;
import com.example.trader.security.details.UserContext;
import com.example.trader.security.service.SecurityService;
import com.example.trader.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NoteBrowseControllerTest {
    @Test void repeatedTickerArgumentsAndOwnerComeFromAuthenticatedContext() throws Exception {
        var service=mock(NoteBrowseService.class);
        when(service.search(eq(7L),any(),eq(0),eq(5))).thenReturn(Page.empty(org.springframework.data.domain.PageRequest.of(0,5)));
        var mvc=MockMvcBuilders.standaloneSetup(new NoteBrowseController(service)).build();
        try(var security=mockStatic(SecurityService.class)) {
            security.when(SecurityService::getAuthenticationUserId).thenReturn(7L);
            mvc.perform(get("/api/note/search").param("userId","999").param("tickers","nvda","AAPL")
                    .param("tickerMatch","ALL").param("q","금리").param("start","2026-11-01").param("end","2026-11-30"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
            verify(service).search(eq(7L),argThat(f->f.tickers().equals(List.of("NVDA","AAPL"))&&f.tickerMatch()==NoteBrowseFilter.TickerMatch.ALL&&f.query().equals("금리")&&f.start().equals(LocalDate.of(2026,11,1))),eq(0),eq(5));
        }
    }
    @Test void calendarBindsMonthAndSameFiltersAndRejectsInvalidInputsBeforeService() throws Exception {
        var service=mock(NoteBrowseService.class);
        when(service.calendar(eq(7L),eq(YearMonth.of(2026,11)),any())).thenReturn(new NoteBrowseService.CalendarMonth("2026-11",List.of(),0));
        var mvc=MockMvcBuilders.standaloneSetup(new NoteBrowseController(service)).build();
        try(var security=mockStatic(SecurityService.class)) {
            security.when(SecurityService::getAuthenticationUserId).thenReturn(7L);
            mvc.perform(get("/api/note/calendar").param("month","2026-11").param("tickers","NVDA","AAPL"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalNotes").value(0));
            verify(service).calendar(eq(7L),eq(YearMonth.of(2026,11)),argThat(f->f.tickers().size()==2&&f.tickerMatch()==NoteBrowseFilter.TickerMatch.ANY));
            clearInvocations(service);
            mvc.perform(get("/api/note/calendar").param("month","2026-13")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/note/search").param("start","2026-02-30")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/note/search").param("tickerMatch","UNKNOWN")).andExpect(status().isBadRequest());
            verifyNoInteractions(service);
        }
    }
    @Test void directUpdateActuallyChangesRecordDateWhileKeepingCreatedTime() {
        var notes=mock(NoteService.class);var users=mock(UserService.class);
        var note=Note.builder().id(1L).subject("old").content("old").stockSymbs(new java.util.ArrayList<>()).noteDate(LocalDate.of(2026,11,3)).build();
        var created=LocalDateTime.of(2026,10,1,9,0);ReflectionTestUtils.setField(note,"createdAt",created);
        when(notes.findNoteById(7L,1L)).thenReturn(note);when(notes.saveNote(note)).thenReturn(1L);
        var principal=new UserContext(ResponseUserDto.builder().id(7L).build(),List.of());
        var request=RequestNoteDto.builder().subject("changed").content("body").noteDate(LocalDate.of(2026,11,5)).stockSymbs(List.of("NVDA","AAPL")).build();
        new NoteController(notes,users).updateNote(request,1L,principal);
        assertThat(note.getNoteDate()).isEqualTo(LocalDate.of(2026,11,5));assertThat(note.getCreatedAt()).isEqualTo(created);
        assertThat(note.getStockSymbs()).containsExactly("NVDA","AAPL");
    }
}
