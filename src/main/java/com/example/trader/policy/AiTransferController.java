package com.example.trader.policy;

import com.example.trader.ai.AiRequestContext;
import com.example.trader.security.details.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AiTransferController {
    private final AiTransferConsent consent;
    public AiTransferController(AiTransferConsent consent) {this.consent=consent;}
    public record Acceptance(String version,String noticeSha256,Boolean accepted) {}
    private ResponseEntity<Map<String,Object>> result(Map<String,Object> body) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
    private void sameSite(HttpServletRequest request) {
        if("cross-site".equals(request.getHeader("Sec-Fetch-Site"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"CROSS_SITE_REJECTED");
    }
    @GetMapping("/api/user/ai-transfer-consent")
    public ResponseEntity<Map<String,Object>> state(@AuthenticationPrincipal UserContext user) {return result(consent.state(user.getUserDto().getId()));}
    @PostMapping("/api/user/ai-transfer-consent")
    public ResponseEntity<Map<String,Object>> accept(@AuthenticationPrincipal UserContext user,@RequestBody Acceptance body,HttpServletRequest request) {
        sameSite(request);
        if(body.version()==null || body.noticeSha256()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"CONSENT_REQUIRED");
        return result(consent.accept(user.getUserDto().getId(),body.version(),body.noticeSha256(),body.accepted()));
    }
    @DeleteMapping("/api/user/ai-transfer-consent")
    public ResponseEntity<Map<String,Object>> withdraw(@AuthenticationPrincipal UserContext user,HttpServletRequest request) {sameSite(request);return result(consent.withdraw(user.getUserDto().getId()));}
    @GetMapping("/internal/ai/retrieval/transfer-consent")
    public ResponseEntity<Map<String,Object>> delegated(HttpServletRequest request) {
        var value=request.getAttribute(AiRequestContext.ATTRIBUTE);
        if(!(value instanceof AiRequestContext c) || c.indexing() || c.userId()==null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"SERVICE_AUTH_REQUIRED");
        return result(consent.state(c.userId()));
    }
}
