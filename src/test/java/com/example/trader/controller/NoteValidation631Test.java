package com.example.trader.controller;

import com.example.trader.common.GlobalExceptionHandler;
import com.example.trader.service.NoteService;
import com.example.trader.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class NoteValidation631Test {
    private final NoteService notes = mock(NoteService.class);
    private final UserService users = mock(UserService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        var validator = new LocalValidatorFactoryBean();
        // A missing provider must fail this regression instead of silently disabling @Valid.
        validator.afterPropertiesSet();
        mvc = standaloneSetup(new NoteController(notes, users))
                .setControllerAdvice(new GlobalExceptionHandler()).setValidator(validator).build();
    }

    private void invalid(String body, String field) throws Exception {
        mvc.perform(post("/api/note/save").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors['" + field + "']").exists());
        verifyNoInteractions(notes, users);
    }

    @Test void rejectsBlankTitleBeforeControllerAndDatabase() throws Exception {
        invalid("{\"subject\":\"  \",\"content\":\"text\",\"noteDate\":\"2026-10-29\"}", "subject");
    }
    @Test void rejectsNullBodyBeforeControllerAndDatabase() throws Exception {
        invalid("{\"subject\":\"title\",\"content\":null,\"noteDate\":\"2026-10-29\"}", "content");
    }
    @Test void rejectsMissingDateBeforeControllerAndDatabase() throws Exception {
        invalid("{\"subject\":\"title\",\"content\":\"text\"}", "noteDate");
    }
    @Test void rejectsInvalidTickerBeforeControllerAndDatabase() throws Exception {
        invalid("{\"subject\":\"title\",\"content\":\"text\",\"noteDate\":\"2026-10-29\",\"stockSymbs\":[\"AA PL\"]}", "stockSymbs[0]");
    }
}
