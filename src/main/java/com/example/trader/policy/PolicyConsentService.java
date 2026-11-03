package com.example.trader.policy;

import com.example.trader.entity.User;
import com.example.trader.service.UserService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyConsentService {
    private final JdbcTemplate jdbc;
    private final PolicyCatalog catalog;
    private final UserService users;

    @Transactional
    public Long register(User user, PolicyAcceptance acceptance) {
        catalog.validate(acceptance); // Reject before any personal-data INSERT.
        Long id=users.createUser(user);
        record(id,acceptance,"LOCAL");
        return id;
    }
    @Transactional
    public User registerOAuth(String provider,String providerId,String email,String name,PolicyAcceptance acceptance) {
        catalog.validate(acceptance);
        User user=users.upsertOAuthUser(provider,providerId,email,name);
        record(user.getId(),acceptance,"OAUTH_"+provider.toUpperCase(java.util.Locale.ROOT));
        return user;
    }
    /** Only provider-verified identities may call this; never match by email. */
    @Transactional
    public User loginOAuth(String provider,String providerId,String email,String name,PolicyAcceptance acceptance) {
        if (provider == null || !List.of("google","kakao","github").contains(provider))
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_provider_identity");
        if (acceptance != null) return registerOAuth(provider,providerId,email,name,acceptance);
        var release=catalog.publicInfo();
        if (!Boolean.TRUE.equals(release.get("published")))
            throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("policy_unavailable");
        var linked=users.findOAuthUser(provider,providerId);
        if (linked.isPresent() && Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT count(*) FROM policy_consent WHERE user_id=? AND version=? AND terms_sha256=? AND privacy_sha256=? AND age14_confirmed=TRUE",
                Integer.class,linked.get().getId(),release.get("version"),release.get("termsSha256"),release.get("privacySha256"))))
            return linked.get();
        // No account, profile, or consent is written until explicit acceptance.
        throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
            new org.springframework.security.oauth2.core.OAuth2Error("policy_consent_required_"+provider));
    }
    @Transactional
    public void record(Long userId, PolicyAcceptance acceptance,String channel) {
        catalog.validate(acceptance);
        if (!List.of("LOCAL","ACCOUNT","OAUTH_GOOGLE","OAUTH_KAKAO","OAUTH_GITHUB").contains(channel))
            throw new IllegalArgumentException("INVALID_CONSENT_CHANNEL");
        int inserted=jdbc.update("""
            INSERT INTO policy_consent(user_id,version,terms_sha256,privacy_sha256,channel,age14_confirmed)
            VALUES(?,?,?,?,?,TRUE) ON CONFLICT(user_id,version) DO NOTHING
            """,userId,acceptance.version(),acceptance.termsSha256(),acceptance.privacySha256(),channel);
        if (inserted==0 && !Integer.valueOf(1).equals(jdbc.queryForObject(
            "SELECT count(*) FROM policy_consent WHERE user_id=? AND version=? AND terms_sha256=? AND privacy_sha256=?",
            Integer.class,userId,acceptance.version(),acceptance.termsSha256(),acceptance.privacySha256())))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"POLICY_RELEASE_CONFLICT");
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> history(Long userId) {
        return jdbc.queryForList("SELECT version,terms_sha256,privacy_sha256,channel,accepted_at FROM policy_consent WHERE user_id=? ORDER BY accepted_at DESC",userId);
    }
}
