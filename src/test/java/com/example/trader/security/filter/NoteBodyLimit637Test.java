package com.example.trader.security.filter;

import com.example.trader.dto.RequestNoteDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class NoteBodyLimit637Test {
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final Echo controller = new Echo();
    NoteBodyLimitFilter filter = new NoteBodyLimitFilter(mapper);
    MockMvc mvc;
    @RestController static class Echo {
        AtomicInteger writes = new AtomicInteger();
        @RequestMapping(path={"/api/note/save","/api/note/update/123"},method={RequestMethod.POST,RequestMethod.PUT})
        Map<String,String> save(@RequestBody RequestNoteDto dto) {
            writes.incrementAndGet(); return Map.of("content",dto.content());
        }
    }
    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("synthetic-owner","",List.of()));
        mvc=standaloneSetup(controller).addFilters(filter).build();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    byte[] body(String content) throws Exception {
        return mapper.writeValueAsBytes(Map.of("subject","합성","content",content,"noteDate","2026-10-29"));
    }
    @Test void longerThanOldEightKiBPreservesMultibyteAndNewlinesExactly() throws Exception {
        var text="가나다\n".repeat(2000);
        mvc.perform(post("/api/note/save").contentType("application/json").content(body(text)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value(text));
        assertEquals(1,controller.writes.get());
    }
    @Test void exactContentBoundaryIsAcceptedButOneByteOverNeverReachesController() throws Exception {
        mvc.perform(put("/api/note/update/123").contentType("application/json").content(body("x".repeat(49152))))
                .andExpect(status().isOk());
        controller.writes.set(0);
        mvc.perform(post("/api/note/save").contentType("application/json").content(body("x".repeat(49153))))
                .andExpect(status().is(413)).andExpect(jsonPath("$.errorCode").value("NOTE_REQUEST_TOO_LARGE"));
        assertEquals(0,controller.writes.get());
    }
    @Test void contentLimitUsesUtf8NotJavaCharacterCount() throws Exception {
        mvc.perform(post("/api/note/save").contentType("application/json").content(body("가".repeat(16384))))
                .andExpect(status().isOk());
        controller.writes.set(0);
        mvc.perform(post("/api/note/save").contentType("application/json").content(body("가".repeat(16385))))
                .andExpect(status().is(413));
        assertEquals(0,controller.writes.get());
    }
    @Test void totalBodyBoundaryIncludesWhitespaceAndMetadata() throws Exception {
        var small=new String(body("ok"),StandardCharsets.UTF_8);
        var exact=small+" ".repeat(65536-small.getBytes(StandardCharsets.UTF_8).length);
        mvc.perform(post("/api/note/save").contentType("application/json").content(exact.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());
        controller.writes.set(0);
        mvc.perform(post("/api/note/save").contentType("application/json").content((exact+" ").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().is(413));
        assertEquals(0,controller.writes.get());
    }
    @Test void unknownContentLengthCannotBypassTotalBound() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/note/save") {
            @Override public int getContentLength(){return -1;}
            @Override public long getContentLengthLong(){return -1;}
        };
        request.setContentType("application/json"); request.setContent(new byte[65537]);
        var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(a,b)->fail("Oversize body reached the application"));
        assertEquals(413,response.getStatus());
    }
    @Test void duplicateKeysMalformedUtf8AndAlternateDecoderCannotBypassValidation() throws Exception {
        mvc.perform(post("/api/note/save").contentType("application/json").content("{\"content\":\"a\",\"content\":\"b\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/note/save").contentType("application/json").content(new byte[]{(byte)0xff}))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/note/save").contentType("application/json;charset=ISO-8859-1").content(body("ok")))
                .andExpect(status().isUnsupportedMediaType());
        assertEquals(0,controller.writes.get());
    }
    @Test void unauthenticatedBodyIsLeftToSecurityAndOtherRoutesAreUnchanged() throws Exception {
        SecurityContextHolder.clearContext();
        var request=new MockHttpServletRequest("POST","/api/note/save");request.setContent(new byte[65537]);
        filter.doFilter(request,new MockHttpServletResponse(),(a,b)->assertSame(request,a));
        setup();
        var other=new MockHttpServletRequest("POST","/api/login/signup");other.setContent(new byte[65537]);
        filter.doFilter(other,new MockHttpServletResponse(),(a,b)->assertSame(other,a));
    }
}
