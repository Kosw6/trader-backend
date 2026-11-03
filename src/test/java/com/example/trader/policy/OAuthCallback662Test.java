package com.example.trader.policy;

import com.example.trader.entity.User;
import com.example.trader.security.oauth2.CustomOAuth2UserService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.OAuth2User;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OAuthCallback662Test {
    OAuth2UserRequest request() {
        var registration=ClientRegistration.withRegistrationId("google").clientId("fixture")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://example.invalid/login/oauth2/code/google")
            .authorizationUri("https://provider.invalid/auth").tokenUri("https://provider.invalid/token")
            .userInfoUri("https://provider.invalid/user").userNameAttributeName("sub").build();
        return new OAuth2UserRequest(registration,new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"fixture-only",java.time.Instant.now(),java.time.Instant.now().plusSeconds(60)));
    }
    @Test void providerIsVerifiedBeforeExistingAccountConsentCheck() {
        var policy=mock(PolicyConsentService.class);var intent=mock(OAuthConsentIntent.class);
        var verified=mock(OAuth2User.class);when(verified.getAttributes()).thenReturn(java.util.Map.of("sub","verified-id","email","a@example.invalid","name","fixture"));
        var user=mock(User.class);when(user.getId()).thenReturn(47L);when(user.getEmail()).thenReturn("a@example.invalid");when(user.getUsername()).thenReturn("fixture");
        when(intent.consume("google")).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"OAUTH_CONSENT_REQUIRED"));
        when(policy.loginOAuth("google","verified-id","a@example.invalid","fixture",null)).thenReturn(user);
        try(var delegates=mockConstruction(DefaultOAuth2UserService.class,(mock,context)->when(mock.loadUser(any())).thenReturn(verified))) {
            var request=request();var result=new CustomOAuth2UserService(policy,intent).loadUser(request);
            assertEquals(47L,(Long)result.getAttribute("id"));
            var order=inOrder(delegates.constructed().get(0),intent,policy);
            order.verify(delegates.constructed().get(0)).loadUser(request);
            order.verify(intent).consume("google");
            order.verify(policy).loginOAuth("google","verified-id","a@example.invalid","fixture",null);
        }
    }
    @Test void failedProviderAuthenticationDoesNotReadOrCreateAccount() {
        var policy=mock(PolicyConsentService.class);var intent=mock(OAuthConsentIntent.class);
        try(var delegates=mockConstruction(DefaultOAuth2UserService.class,(mock,context)->when(mock.loadUser(any())).thenThrow(new OAuth2AuthenticationException("invalid_token")))) {
            assertThrows(OAuth2AuthenticationException.class,()->new CustomOAuth2UserService(policy,intent).loadUser(request()));
            verifyNoInteractions(policy,intent);
        }
    }
}
