package com.example.trader.emailverification;

import com.example.trader.controller.loginController;
import com.example.trader.policy.PolicyConsentService;
import com.example.trader.service.UserService;
import com.example.trader.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EmailVerificationControllerTest {
    @Test void publicEndpointsReturnNoStoreAndDoNotTrustForwardedIp() throws Exception {
        var service=mock(EmailVerificationService.class);
        when(service.enabled()).thenReturn(true);
        when(service.request("a@example.com","127.0.0.1")).thenReturn(new EmailVerificationService.Requested("opaque",600,60));
        when(service.confirm("a@example.com","opaque","123456")).thenReturn(new EmailVerificationService.Verified("proof",600));
        var mvc=MockMvcBuilders.standaloneSetup(new EmailVerificationController(service))
                .setControllerAdvice(new EmailVerificationExceptionHandler()).build();
        mvc.perform(get("/api/login/email-verification/config")).andExpect(status().isOk()).andExpect(jsonPath("$.required").value(true));
        mvc.perform(post("/api/login/email-verification/requests").header("X-Forwarded-For","untrusted")
                .contentType("application/json").content("{\"email\":\"a@example.com\"}"))
                .andExpect(status().isAccepted()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.challengeId").value("opaque")).andExpect(jsonPath("$.code").doesNotExist());
        verify(service).request("a@example.com","127.0.0.1");
        mvc.perform(post("/api/login/email-verification/confirm").contentType("application/json")
                .content("{\"email\":\"a@example.com\",\"challengeId\":\"opaque\",\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.emailVerificationToken").value("proof"));
    }
    @Test void unverifiedSignupNeverWritesAccountOrConsent() throws Exception {
        var service=mock(EmailVerificationService.class);var policy=mock(PolicyConsentService.class);var users=mock(UserService.class);
        when(service.consumeForSignup(anyString(),nullable(String.class))).thenThrow(new EmailVerificationException(HttpStatus.BAD_REQUEST,"EMAIL_VERIFICATION_REQUIRED"));
        var mvc=MockMvcBuilders.standaloneSetup(new loginController(null,null,users,policy,service))
                .setControllerAdvice(new EmailVerificationExceptionHandler()).build();
        mvc.perform(post("/api/login/signup").contentType("application/json").content(signup(false)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
        verifyNoInteractions(policy,users);
    }
    @Test void verifiedSignupBindsEmailAndCannotRequestAdminRole() throws Exception {
        var service=mock(EmailVerificationService.class);var policy=mock(PolicyConsentService.class);
        when(service.consumeForSignup("A@example.com","proof")).thenReturn("a@example.com");
        var mvc=MockMvcBuilders.standaloneSetup(new loginController(null,null,mock(UserService.class),policy,service)).build();
        mvc.perform(post("/api/login/signup").contentType("application/json").content(signup(true)))
                .andExpect(status().isCreated()).andExpect(header().doesNotExist("Set-Cookie"));
        var account=ArgumentCaptor.forClass(User.class);verify(policy).register(account.capture(),isNull());
        assertEquals("a@example.com",account.getValue().getEmail());
        assertEquals(com.example.trader.entity.Role.USER,account.getValue().getRole());
    }
    private String signup(boolean proof) {
        return "{\"loginId\":\"synthetic\",\"password\":\"not-a-real-password\",\"email\":\"A@example.com\",\"nickName\":\"fixture\",\"role\":\"ADMIN\""+
                (proof?",\"emailVerificationToken\":\"proof\"":"")+"}";
    }
}
