package com.example.trader.security.provider;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.trader.dto.ResponseUserDto;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.security.details.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** RS256 전용. 기존 HMAC 토큰은 재로그인으로 전환한다. */
@Service
public class JwtTokenProvider {
    private final JwtKeyRing keys;
    private final String issuer, audience, refreshAudience;
    private final long accessSeconds, refreshSeconds, leeway;

    public JwtTokenProvider(JwtKeyRing keys,
            @Value("${auth.jwt.issuer:trader-backend}") String issuer,
            @Value("${auth.jwt.audience:trader-services}") String audience,
            @Value("${auth.jwt.refresh-audience:trader-refresh}") String refreshAudience,
            @Value("${auth.jwt.access-seconds:1800}") long accessSeconds,
            @Value("${auth.jwt.refresh-seconds:6000}") long refreshSeconds,
            @Value("${auth.jwt.clock-skew-seconds:30}") long leeway) {
        if (issuer.isBlank() || audience.isBlank() || refreshAudience.isBlank()
                || audience.equals(refreshAudience) || accessSeconds <= 0 || refreshSeconds <= 0
                || accessSeconds > 86400 || refreshSeconds > 2592000 || leeway < 0 || leeway > 120)
            throw new IllegalArgumentException("JWT 발급/검증 설정이 유효하지 않습니다.");
        this.keys = keys; this.issuer = issuer; this.audience = audience;
        this.refreshAudience = refreshAudience; this.accessSeconds = accessSeconds;
        this.refreshSeconds = refreshSeconds; this.leeway = leeway;
    }
    public long accessTokenSeconds() { return accessSeconds; }
    public long refreshTokenSeconds() { return refreshSeconds; }
    public String createAccessToken(Long id, String login, String role, String nick) throws UnsupportedEncodingException {
        return issue(id, login, role, nick, "access", audience, accessSeconds);
    }
    public String createRefreshToken(Long id, String login, String role, String nick) throws UnsupportedEncodingException {
        return issue(id, login, role, nick, "refresh", refreshAudience, refreshSeconds);
    }
    private String issue(Long id, String login, String role, String nick, String use, String target, long seconds) {
        if (id == null || id <= 0 || login == null || login.isBlank() || role == null || role.isBlank() || nick == null || nick.isBlank())
            throw new BaseException(BaseResponseStatus.INVALID_JWT_TOKEN);
        Instant now = Instant.now();
        return JWT.create().withKeyId(keys.activeKid()).withSubject(login).withIssuer(issuer).withAudience(target)
                .withClaim("token_use", use).withClaim("userId", id).withClaim("role", role).withClaim("nickName", nick)
                .withIssuedAt(Date.from(now)).withExpiresAt(Date.from(now.plusSeconds(seconds)))
                .sign(Algorithm.RSA256(keys.publicKey(keys.activeKid()), keys.privateKey()));
    }
    /** REST/WS 기존 호출은 Access 전용이다. */
    public DecodedJWT validateTokenOrThrow(String token) { return verify(token, "access", audience); }
    public DecodedJWT validateRefreshTokenOrThrow(String token) { return verify(token, "refresh", refreshAudience); }
    private DecodedJWT verify(String token, String use, String target) {
        try {
            if (token == null || token.length() > 16384) throw new IllegalArgumentException();
            var header = JWT.decode(token);
            if (!"RS256".equals(header.getAlgorithm()) || header.getKeyId() == null
                    || !header.getKeyId().matches("[A-Za-z0-9_-]{1,64}")
                    || !header.getHeaderClaim("crit").isNull() || !header.getHeaderClaim("jku").isNull()
                    || !header.getHeaderClaim("x5u").isNull()) throw new IllegalArgumentException();
            var jwt = JWT.require(Algorithm.RSA256(keys.publicKey(header.getKeyId()), null))
                    .withIssuer(issuer).withAudience(target).withClaim("token_use", use).acceptLeeway(leeway).build().verify(token);
            if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null || !jwt.getExpiresAt().after(jwt.getIssuedAt())
                    || jwt.getIssuedAt().toInstant().isAfter(Instant.now().plusSeconds(leeway))
                    || jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || jwt.getClaim("userId").asLong() == null || jwt.getClaim("userId").asLong() <= 0
                    || jwt.getClaim("role").asString() == null || jwt.getClaim("role").asString().isBlank()
                    || jwt.getClaim("nickName").asString() == null || jwt.getClaim("nickName").asString().isBlank()) throw new IllegalArgumentException();
            return jwt;
        } catch (TokenExpiredException e) {
            throw new BaseException(BaseResponseStatus.ACCESS_TOKEN_EXPIRED);
        } catch (Exception e) {
            throw new BaseException(BaseResponseStatus.INVALID_JWT_TOKEN);
        }
    }
    public DecodedJWT validateToken(String token) {
        try { return validateTokenOrThrow(token); } catch (BaseException e) { return null; }
    }
    public String getTokenInfo(String token) throws UnsupportedEncodingException { return validateTokenOrThrow(token).getSubject(); }
    public String getTokenSubject(DecodedJWT jwt) { return jwt.getSubject(); }
    public String resolveToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) if ("accessToken".equals(cookie.getName())) return cookie.getValue();
        return null;
    }
    public Authentication getAuthentication(DecodedJWT jwt) {
        var userDto = ResponseUserDto.builder().id(jwt.getClaim("userId").asLong()).loginId(jwt.getSubject())
                .role(jwt.getClaim("role").asString()).nickName(jwt.getClaim("nickName").asString()).build();
        var authorities = List.of(new SimpleGrantedAuthority(jwt.getClaim("role").asString()));
        var context = new UserContext(userDto, authorities);
        return new UsernamePasswordAuthenticationToken(context, "", context.getAuthorities());
    }
}
