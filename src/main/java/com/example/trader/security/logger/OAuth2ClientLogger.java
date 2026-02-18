package com.example.trader.security.logger;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2ClientLogger {

    private final ClientRegistrationRepository repo;

    @PostConstruct
    public void logClient() {
        ClientRegistration google = repo.findByRegistrationId("google");
        log.info(">>> GOOGLE client-id = {}", google.getClientId());
        log.info(">>> GOOGLE redirect-uri = {}", google.getRedirectUri());
    }
}
