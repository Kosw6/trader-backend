package com.example.trader.policy;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiTransferConsent {
    private final JdbcTemplate jdbc;
    private final PolicyCatalog catalog;
    private final String noticeHash;
    public AiTransferConsent(JdbcTemplate jdbc,PolicyCatalog catalog,
        @Value("${app.policy.ai-transfer-sha256:}") String noticeHash) {
        this.jdbc=jdbc;this.catalog=catalog;this.noticeHash=noticeHash;
    }
    @Transactional(readOnly=true)
    public Map<String,Object> state(Long userId) {
        var meta=catalog.publicInfo();String version=(String)meta.get("version");
        boolean published=Boolean.TRUE.equals(meta.get("published")) && noticeHash.matches("[a-f0-9]{64}");
        boolean accepted=false;
        if(published) {
            var rows=jdbc.queryForList("SELECT version,notice_sha256,accepted FROM ai_transfer_consent WHERE user_id=? ORDER BY id DESC LIMIT 1",userId);
            if(!rows.isEmpty()) {var row=rows.get(0);accepted=version.equals(row.get("version")) && noticeHash.equals(row.get("notice_sha256")) && Boolean.TRUE.equals(row.get("accepted"));}
        }
        return Map.of("published",published,"version",version,"noticeSha256",noticeHash,"accepted",accepted);
    }
    @Transactional
    public Map<String,Object> accept(Long userId,String version,String hash,Boolean accepted) {
        var current=state(userId);
        if(!Boolean.TRUE.equals(current.get("published"))) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"POLICY_NOT_PUBLISHED");
        if(!Boolean.TRUE.equals(accepted)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"CONSENT_REQUIRED");
        if(!version.equals(current.get("version")) || !hash.equals(noticeHash)) throw new ResponseStatusException(HttpStatus.CONFLICT,"POLICY_CHANGED");
        if(!Boolean.TRUE.equals(current.get("accepted"))) append(userId,true);
        return state(userId);
    }
    @Transactional
    public Map<String,Object> withdraw(Long userId) { append(userId,false);return state(userId); }
    private void append(Long userId,boolean accepted) {
        jdbc.update("INSERT INTO ai_transfer_consent(user_id,version,notice_sha256,accepted) VALUES(?,?,?,?)",userId,catalog.publicInfo().get("version"),noticeHash,accepted);
    }
}
