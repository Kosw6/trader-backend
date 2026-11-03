package com.example.trader.emailverification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailVerificationServiceTest {
    final EmailVerificationStore store = mock(EmailVerificationStore.class);
    final VerificationMailSender sender = mock(VerificationMailSender.class);
    final EmailVerificationProperties properties = new EmailVerificationProperties(true,"ap-northeast-2","noreply@example.invalid","s".repeat(40),"");
    final EmailVerificationService service = new EmailVerificationService(properties,store,sender);
    @BeforeEach void setup() { when(store.issue(anyString(),anyString(),anyString(),anyString())).thenReturn(true); }

    @Test void requestSendsSixDigitsButOnlyReturnsChallenge() {
        var requested = service.request(" Person@Example.COM ","127.0.0.1");
        var code = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("person@example.com"),code.capture());
        assertTrue(code.getValue().matches("[0-9]{6}"));
        assertTrue(requested.challengeId().matches("[a-f0-9]{64}"));
        assertEquals(600,requested.expiresInSeconds());
        assertEquals(60,requested.resendAfterSeconds());
        var hash = ArgumentCaptor.forClass(String.class);
        verify(store).issue(hash.capture(),anyString(),eq(requested.challengeId()),anyString());
        assertTrue(hash.getValue().matches("[a-f0-9]{64}"));
        assertFalse(hash.getValue().contains("person"));
    }
    @Test void otpIsBoundToChallengeAndEmailAndOnlyHashOfProofIsStored() {
        var requested = service.request("person@example.com","ip");
        var code = ArgumentCaptor.forClass(String.class); verify(sender).send(anyString(),code.capture());
        var emailHash=ArgumentCaptor.forClass(String.class); var codeMac=ArgumentCaptor.forClass(String.class);
        verify(store).issue(emailHash.capture(),anyString(),anyString(),codeMac.capture());
        when(store.confirm(eq(emailHash.getValue()),eq(requested.challengeId()),eq(codeMac.getValue()),anyString())).thenReturn(true);
        var verified=service.confirm("PERSON@example.com",requested.challengeId(),code.getValue());
        assertTrue(verified.emailVerificationToken().matches("[a-f0-9]{64}"));
        var proofHash=ArgumentCaptor.forClass(String.class);
        verify(store).confirm(eq(emailHash.getValue()),eq(requested.challengeId()),eq(codeMac.getValue()),proofHash.capture());
        assertNotEquals(verified.emailVerificationToken(),proofHash.getValue());
        when(store.consume(emailHash.getValue(),proofHash.getValue())).thenReturn(true);
        assertEquals("person@example.com",service.consumeForSignup("Person@example.com",verified.emailVerificationToken()));
        assertThrows(EmailVerificationException.class,()->service.confirm("other@example.com",requested.challengeId(),code.getValue()));
    }
    @Test void rateLimitDoesNotSendMail() {
        when(store.issue(anyString(),anyString(),anyString(),anyString())).thenReturn(false);
        assertEquals(429,assertThrows(EmailVerificationException.class,()->service.request("a@example.com","ip")).status().value());
        verifyNoInteractions(sender);
    }
    @Test void sendFailureInvalidatesOnlyOwnChallengeAndHidesProviderDetails() {
        doThrow(new IllegalStateException("private AWS payload")).when(sender).send(anyString(),anyString());
        var error=assertThrows(EmailVerificationException.class,()->service.request("a@example.com","ip"));
        assertEquals("EMAIL_DELIVERY_UNAVAILABLE",error.code()); assertNull(error.getCause());
        verify(store).cancel(anyString(),anyString());
    }
    @Test void redisFailureNeverFallsBackOrSendsMail() {
        when(store.issue(anyString(),anyString(),anyString(),anyString())).thenThrow(new IllegalStateException("private Redis payload"));
        assertEquals(503,assertThrows(EmailVerificationException.class,()->service.request("a@example.com","ip")).status().value());
        verifyNoInteractions(sender);
        when(store.consume(anyString(),anyString())).thenThrow(new IllegalStateException());
        assertEquals(503,assertThrows(EmailVerificationException.class,()->service.consumeForSignup("a@example.com","a".repeat(64))).status().value());
    }
    @Test void missingInvalidAndUsedProofRejected() {
        for (String token : new String[]{null,"","a".repeat(64)})
            assertEquals("EMAIL_VERIFICATION_REQUIRED",assertThrows(EmailVerificationException.class,
                    ()->service.consumeForSignup("a@example.com",token)).code());
    }
    @Test void disabledRolloutDoesNotUseRedisSesOrChangeLegacyEmail() {
        var disabled=new EmailVerificationService(new EmailVerificationProperties(false,"ap-northeast-2","","",""),store,sender);
        assertEquals("A@example.com",disabled.consumeForSignup("A@example.com",null));
        assertThrows(EmailVerificationException.class,()->disabled.request("a@example.com","ip"));
        assertThrows(EmailVerificationException.class,()->disabled.confirm("a@example.com","a","123456"));
        verifyNoInteractions(store,sender);
    }
    @Test void invalidInputRejectedBeforeRedisAndSes() {
        for (String email : new String[]{null,"bad","x\r\nBcc:y@example.com","a..b@example.com",".a@example.com","a@-example.com","a@localhost","a".repeat(65)+"@example.com"})
            assertThrows(EmailVerificationException.class,()->service.request(email,"ip"));
        assertThrows(EmailVerificationException.class,()->service.confirm("a@example.com","bad","123456"));
        assertThrows(EmailVerificationException.class,()->service.confirm("a@example.com","a".repeat(64),"12345"));
        verifyNoInteractions(store,sender);
    }
    @Test void enabledConfigurationMustHaveSenderAndStrongSecret() {
        assertThrows(IllegalArgumentException.class,()->new EmailVerificationProperties(true,"ap-northeast-2","","s".repeat(40),""));
        assertThrows(IllegalArgumentException.class,()->new EmailVerificationProperties(true,"ap-northeast-2","a@example.com","short",""));
    }
}
