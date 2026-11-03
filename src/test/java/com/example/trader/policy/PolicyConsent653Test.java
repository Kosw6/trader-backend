package com.example.trader.policy;

import com.example.trader.entity.User;
import com.example.trader.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PolicyConsent653Test {
    final String terms="a".repeat(64), privacy="b".repeat(64);
    final PolicyCatalog catalog=new PolicyCatalog(true,"653-test",terms,privacy);
    PolicyAcceptance accepted(){return new PolicyAcceptance("653-test",terms,privacy,true,true,true);}
    @Test void missingFalseAndStaleAreRejectedBeforeAccountCreation() {
        var jdbc=mock(JdbcTemplate.class);var users=mock(UserService.class);
        var service=new PolicyConsentService(jdbc,catalog,users);
        for(var value:java.util.Arrays.asList(null,new PolicyAcceptance("653-test",terms,privacy,false,true,true),new PolicyAcceptance("old",terms,privacy,true,true,true),new PolicyAcceptance("653-test","c".repeat(64),privacy,true,true,true)))
            assertThrows(ResponseStatusException.class,()->service.register(mock(User.class),value));
        verifyNoInteractions(users,jdbc);
    }
    @Test void inactiveAndDraftCannotBeActivated() {
        assertThrows(ResponseStatusException.class,()->new PolicyCatalog(false,"","","").validate(accepted()));
        assertThrows(IllegalArgumentException.class,()->new PolicyCatalog(true,"draft.1",terms,privacy));
    }
    @Test void accountIdentityAndChannelComeFromServer() {
        var jdbc=mock(JdbcTemplate.class);var users=mock(UserService.class);var user=mock(User.class);
        when(users.createUser(user)).thenReturn(47L);
        when(jdbc.update(anyString(),eq(47L),any(),any(),any(),any())).thenReturn(1);
        var service=new PolicyConsentService(jdbc,catalog,users);
        assertEquals(47L,service.register(user,accepted()));
        verify(jdbc).update(anyString(),eq(47L),eq("653-test"),eq(terms),eq(privacy),eq("LOCAL"));
    }
    @Test void oauthIntentIsSessionBoundProviderBoundAndSingleUse() {
        var intents=new OAuthConsentIntent(catalog);var request=new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThrows(ResponseStatusException.class,()->intents.consume("google"));
            intents.prepare(request,"google",accepted());
            assertThrows(ResponseStatusException.class,()->intents.consume("kakao"));
            assertThrows(ResponseStatusException.class,()->intents.consume("google"));
            intents.prepare(request,"google",accepted());
            assertEquals(accepted(),intents.consume("google"));
            assertThrows(ResponseStatusException.class,()->intents.consume("google"));
        } finally {RequestContextHolder.resetRequestAttributes();}
    }
    @Test void oauthCannotPrepareCrossSiteOrUnknownProvider() {
        var intents=new OAuthConsentIntent(catalog);var request=new MockHttpServletRequest();
        request.addHeader("Sec-Fetch-Site","cross-site");
        assertThrows(ResponseStatusException.class,()->intents.prepare(request,"google",accepted()));
        assertNull(request.getSession(false));
        assertThrows(ResponseStatusException.class,()->intents.prepare(new MockHttpServletRequest(),"evil",accepted()));
    }
    @Test void expiredOAuthIntentCannotBeReused() {
        var request=new MockHttpServletRequest();
        var clock=java.time.Clock.fixed(java.time.Instant.parse("2026-10-29T00:00:00Z"),java.time.ZoneOffset.UTC);
        new OAuthConsentIntent(catalog,clock).prepare(request,"google",accepted());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            var expired=new OAuthConsentIntent(catalog,java.time.Clock.offset(clock,java.time.Duration.ofMinutes(10)));
            assertThrows(ResponseStatusException.class,()->expired.consume("google"));
            assertThrows(ResponseStatusException.class,()->new OAuthConsentIntent(catalog,clock).consume("google"));
        } finally {RequestContextHolder.resetRequestAttributes();}
    }
    @Test void oauthRecordFailureDoesNotGetSwallowed() {
        var users=mock(UserService.class);var jdbc=mock(JdbcTemplate.class);
        var user=mock(User.class);when(user.getId()).thenReturn(91L);
        when(users.upsertOAuthUser("google","provider-id","user@example.invalid","test")).thenReturn(user);
        when(jdbc.update(anyString(),eq(91L),any(),any(),any(),any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("fixture"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->new PolicyConsentService(jdbc,catalog,users).registerOAuth("google","provider-id","user@example.invalid","test",accepted()));
    }
    @Test void reusedVersionCannotSilentlyAcceptDifferentDocuments() {
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(47L),any(),any(),any())).thenReturn(0);
        var service=new PolicyConsentService(jdbc,catalog,mock(UserService.class));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.record(47L,accepted(),"LOCAL")).getStatusCode().value());
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(47L),any(),any(),any())).thenReturn(1);
        assertDoesNotThrow(()->service.record(47L,accepted(),"LOCAL"));
    }
    @Test void httpPolicyErrorsPreserveSafeCodeAndStatus() throws Exception {
        var policy=mock(PolicyCatalog.class);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
            new PolicyController(policy,mock(OAuthConsentIntent.class),mock(PolicyConsentService.class)))
            .setControllerAdvice(new PolicyExceptionHandler()).build();
        for(var status:java.util.List.of(400,409,503)) {
            doThrow(new ResponseStatusException(org.springframework.http.HttpStatusCode.valueOf(status),"POLICY_CHANGED")).when(policy).publicInfo();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/login/policies"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is(status))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("POLICY_CHANGED"));
        }
    }
}
