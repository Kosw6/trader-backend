package com.example.trader.policy;

import com.example.trader.security.details.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PolicyController {
    private final PolicyCatalog catalog;
    private final OAuthConsentIntent intents;
    private final PolicyConsentService consents;
    @GetMapping("/api/login/policies")
    public Map<String,Object> policies() {return catalog.publicInfo();}
    public record OAuthRequest(String provider,PolicyAcceptance consent) {}
    @PostMapping("/api/login/consent-intent")
    public Map<String,Boolean> intent(@RequestBody OAuthRequest body,HttpServletRequest request) {
        intents.prepare(request,body.provider(),body.consent());return Map.of("prepared",true);
    }
    @GetMapping("/api/user/consents")
    public List<Map<String,Object>> history(@AuthenticationPrincipal UserContext user) {
        return consents.history(user.getUserDto().getId());
    }
}
