package com.example.trader.policy;

import jakarta.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class OAuthConsentIntent {
    private static final String KEY=OAuthConsentIntent.class.getName();
    private record Intent(String provider,PolicyAcceptance acceptance,Instant expires) implements Serializable {}
    private final PolicyCatalog catalog;
    private final java.time.Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public OAuthConsentIntent(PolicyCatalog catalog) { this(catalog,java.time.Clock.systemUTC()); }
    OAuthConsentIntent(PolicyCatalog catalog,java.time.Clock clock) { this.catalog=catalog;this.clock=clock; }
    public void prepare(HttpServletRequest request,String provider,PolicyAcceptance acceptance) {
        if (!Set.of("google","kakao","github").contains(provider)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_PROVIDER");
        if ("cross-site".equals(request.getHeader("Sec-Fetch-Site"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"CROSS_SITE_REJECTED");
        catalog.validate(acceptance);
        request.getSession(true).setAttribute(KEY,new Intent(provider,acceptance,Instant.now(clock).plusSeconds(600)));
    }
    public PolicyAcceptance consume(String provider) {
        var attrs=RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servlet)) throw missing();
        var session=servlet.getRequest().getSession(false);
        if (session==null) throw missing();
        Intent intent;
        synchronized(session) {
            var value=session.getAttribute(KEY);session.removeAttribute(KEY);
            if (!(value instanceof Intent)) throw missing();
            intent=(Intent)value;
        }
        if (!intent.provider().equals(provider) || !Instant.now(clock).isBefore(intent.expires())) throw missing();
        catalog.validate(intent.acceptance());return intent.acceptance();
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"OAUTH_CONSENT_REQUIRED"); }
}
