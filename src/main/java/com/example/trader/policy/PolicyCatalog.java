package com.example.trader.policy;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Activation requires an explicitly reviewed, immutable document release. */
@Component
public class PolicyCatalog {
    private final boolean enabled;
    private final String version, termsHash, privacyHash;
    public PolicyCatalog(@Value("${app.policy.enabled:false}") boolean enabled,
                         @Value("${app.policy.version:}") String version,
                         @Value("${app.policy.terms-sha256:}") String termsHash,
                         @Value("${app.policy.privacy-sha256:}") String privacyHash) {
        this.enabled=enabled; this.version=version; this.termsHash=termsHash; this.privacyHash=privacyHash;
        if (enabled && (!version.matches("[A-Za-z0-9.-]{1,64}") || version.contains("draft") ||
            !termsHash.matches("[a-f0-9]{64}") || !privacyHash.matches("[a-f0-9]{64}")))
            throw new IllegalArgumentException("INVALID_POLICY_RELEASE");
    }
    public void validate(PolicyAcceptance a) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"POLICY_NOT_PUBLISHED");
        if (a==null || !Boolean.TRUE.equals(a.termsAccepted()) || !Boolean.TRUE.equals(a.personalDataAccepted()) || !Boolean.TRUE.equals(a.age14Confirmed()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"CONSENT_REQUIRED");
        if (!version.equals(a.version()) || !termsHash.equals(a.termsSha256()) || !privacyHash.equals(a.privacySha256()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"POLICY_CHANGED");
    }
    public Map<String,Object> publicInfo() {
        return Map.of("published",enabled,"version",version,"termsSha256",termsHash,"privacySha256",privacyHash);
    }
}
