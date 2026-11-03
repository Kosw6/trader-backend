package com.example.trader.controller;
import com.example.trader.security.service.SecurityService;
import com.example.trader.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class NoteCalendarControllerTest {
    @Test void periodReadAndWriteAlwaysUseAuthenticatedOwnerNotClientOwner() throws Exception {
        var service=mock(NoteCalendarService.class);var mvc=MockMvcBuilders.standaloneSetup(new NoteCalendarController(service)).build();
        when(service.window(eq(7L),eq(NoteCalendarPeriod.Unit.MONTH),eq("2021"),any())).thenReturn(new NoteCalendarService.Window(NoteCalendarPeriod.Unit.MONTH,"2021",List.of(),0));
        when(service.save(eq(7L),any())).thenReturn(new NoteCalendarService.Performance("123.45","2.5","USD",0L));
        try(var security=mockStatic(SecurityService.class)){
            security.when(SecurityService::getAuthenticationUserId).thenReturn(7L);
            mvc.perform(get("/api/note/calendar/periods").param("unit","MONTH").param("anchor","2021").param("userId","999").param("tickers","NVDA","AAPL").param("tickerMatch","ALL"))
                .andExpect(status().isOk());
            verify(service).window(eq(7L),eq(NoteCalendarPeriod.Unit.MONTH),eq("2021"),argThat(f->f.tickers().equals(List.of("NVDA","AAPL"))&&f.tickerMatch()==NoteBrowseFilter.TickerMatch.ALL));
            mvc.perform(put("/api/note/calendar/performance").contentType(MediaType.APPLICATION_JSON).content("{\"unit\":\"YEAR\",\"key\":\"2021\",\"amount\":\"123.45\",\"rate\":\"2.5\",\"currency\":\"USD\",\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value("123.45"));
            verify(service).save(eq(7L),argThat(r->r.unit()==NoteCalendarPeriod.Unit.YEAR&&r.key().equals("2021")&&r.version()==null));
        }
    }
    @Test void unknownUnitAndMissingAnchorFailWithoutQuery() throws Exception {
        var service=mock(NoteCalendarService.class);var mvc=MockMvcBuilders.standaloneSetup(new NoteCalendarController(service)).build();
        mvc.perform(get("/api/note/calendar/periods").param("unit","WEEK").param("anchor","2021")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/note/calendar/periods").param("unit","YEAR")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void tagEndpointIgnoresClientOwnerAndBindsPeriodVersion() throws Exception {
        var service=mock(NoteCalendarService.class);var mvc=MockMvcBuilders.standaloneSetup(new NoteCalendarController(service)).build();
        when(service.saveTags(eq(7L),any())).thenReturn(new NoteCalendarService.Performance(null,null,"KRW",0L,List.of("반도체","방산")));
        try(var security=mockStatic(SecurityService.class)){
            security.when(SecurityService::getAuthenticationUserId).thenReturn(7L);
            mvc.perform(put("/api/note/calendar/tags").contentType(MediaType.APPLICATION_JSON).content("{\"unit\":\"MONTH\",\"key\":\"2021-01\",\"tags\":[\"반도체\",\"방산\"],\"version\":2,\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags[0]").value("반도체"));
            verify(service).saveTags(eq(7L),argThat(r->r.key().equals("2021-01")&&r.version()==2L&&r.tags().equals(List.of("반도체","방산"))));
        }
    }
}
