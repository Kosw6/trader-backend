package com.example.trader.policy;

import com.example.trader.entity.User;
import com.example.trader.service.UserService;
import com.example.trader.security.oauth2.OAuthFailureRoute;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthReturning662Test {
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final UserService users=mock(UserService.class);
    final String terms="a".repeat(64),privacy="b".repeat(64);
    final PolicyCatalog catalog=new PolicyCatalog(true,"662-test",terms,privacy);
    final PolicyConsentService service=new PolicyConsentService(jdbc,catalog,users);
    User linked(String provider) {
        User user=mock(User.class);when(user.getId()).thenReturn(47L);
        when(users.findOAuthUser(provider,"verified-sub")).thenReturn(Optional.of(user));
        return user;
    }
    void consentCount(int count) {
        when(jdbc.queryForObject(contains("age14_confirmed=TRUE"),eq(Integer.class),eq(47L),eq("662-test"),eq(terms),eq(privacy))).thenReturn(count);
    }
    @Test void currentConsentSkipsScreenWithoutUpdatingAccountOrConsent() {
        for(String provider:java.util.List.of("google","kakao")) {
            User user=linked(provider);consentCount(1);
            assertSame(user,service.loginOAuth(provider,"verified-sub","ignored@example.invalid","ignored",null));
        }
        verify(users,never()).upsertOAuthUser(any(),any(),any(),any());
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void newAccountIsNotCreatedUntilConsent() {
        when(users.findOAuthUser("google","verified-sub")).thenReturn(Optional.empty());
        var error=assertThrows(OAuth2AuthenticationException.class,()->service.loginOAuth("google","verified-sub","same-as-local@example.invalid","name",null));
        assertEquals("/consent/oauth/google",OAuthFailureRoute.destination(error));
        verify(users,never()).upsertOAuthUser(any(),any(),any(),any());verifyNoInteractions(jdbc);
    }
    @Test void missingOrStaleConsentRequiresExplicitAcceptance() {
        linked("kakao");consentCount(0);
        var error=assertThrows(OAuth2AuthenticationException.class,()->service.loginOAuth("kakao","verified-sub","a@example.invalid","name",null));
        assertEquals("/consent/oauth/kakao",OAuthFailureRoute.destination(error));
        verify(users,never()).upsertOAuthUser(any(),any(),any(),any());
    }
    @Test void explicitConsentStillRegistersAndRecords() {
        User user=mock(User.class);when(user.getId()).thenReturn(47L);
        when(users.upsertOAuthUser("google","verified-sub","a@example.invalid","name")).thenReturn(user);
        when(jdbc.update(anyString(),eq(47L),any(),any(),any(),any())).thenReturn(1);
        assertSame(user,service.loginOAuth("google","verified-sub","a@example.invalid","name",new PolicyAcceptance("662-test",terms,privacy,true,true,true)));
        verify(jdbc).update(anyString(),eq(47L),eq("662-test"),eq(terms),eq(privacy),eq("OAUTH_GOOGLE"));
    }
    @Test void unavailablePolicyOrDatabaseFailureCannotBypassConsent() {
        assertThrows(OAuth2AuthenticationException.class,()->new PolicyConsentService(jdbc,new PolicyCatalog(false,"","",""),users).loginOAuth("google","verified-sub","a","b",null));
        verifyNoInteractions(users,jdbc);
        linked("google");
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(47L),any(),any(),any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("fixture"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->service.loginOAuth("google","verified-sub","a","b",null));
    }
    @Test void onlyFixedConsentDestinationsAreAllowed() {
        for(String code:java.util.List.of("policy_consent_required_https://evil.invalid","invalid_token","account_link_required","policy_consent_required_github"))
            assertEquals("/sign-in?oauth2_error",OAuthFailureRoute.destination(new OAuth2AuthenticationException(new OAuth2Error(code))));
    }
}
