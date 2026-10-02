package com.example.trader.security.provider;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.trader.controller.JwksController;
import com.example.trader.controller.loginController;
import com.example.trader.exception.BaseException;
import com.example.trader.security.filter.JwtFilter;
import com.example.trader.security.service.FormUserDetailService;
import com.example.trader.service.UserService;
import com.example.trader.ws.interceptor.JwtHandshakeInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationContractTest {
    @TempDir Path temp;
    JwtKeyRing ring;
    JwtTokenProvider provider;
    KeyPair pair;

    @BeforeEach void setup() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        pair = generator.generateKeyPair();
        Files.createDirectories(temp.resolve("public"));
        Files.writeString(temp.resolve("private.pem"), pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.writeString(temp.resolve("public/k1.pem"), pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        ring = new JwtKeyRing("k1", temp.resolve("private.pem").toString(), temp.resolve("public").toString());
        provider = new JwtTokenProvider(ring, "trader-backend", "trader-services", "trader-refresh", 1800, 6000, 0);
    }
    static String pem(String type, byte[] bytes) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(bytes)
                + "\n-----END " + type + "-----\n";
    }
    String access() throws Exception { return provider.createAccessToken(1L, "test-user", "ROLE_USER", "테스트"); }
    String refresh() throws Exception { return provider.createRefreshToken(1L, "test-user", "ROLE_USER", "테스트"); }

    @Test void access_refresh_and_instance_contract() throws Exception {
        var second = new JwtTokenProvider(ring, "trader-backend", "trader-services", "trader-refresh", 1800, 6000, 0);
        assertThat(second.validateTokenOrThrow(access()).getClaim("userId").asLong()).isEqualTo(1);
        assertThat(provider.validateRefreshTokenOrThrow(refresh()).getClaim("token_use").asString()).isEqualTo("refresh");
        assertThatThrownBy(() -> provider.validateTokenOrThrow(refresh())).isInstanceOf(BaseException.class);
        assertThatThrownBy(() -> provider.validateRefreshTokenOrThrow(access())).isInstanceOf(BaseException.class);
    }
    @Test void rejects_old_hmac_unknown_key_wrong_claims_expired_and_tampered() throws Exception {
        String old = JWT.create().withSubject("test-user").sign(Algorithm.HMAC256("synthetic-test-key-only"));
        assertThatThrownBy(() -> provider.validateTokenOrThrow(old)).isInstanceOf(BaseException.class);
        for (String problem : List.of("kid", "issuer", "audience", "expired", "future", "missing-exp")) {
            Instant now = Instant.now();
            var builder = JWT.create().withKeyId(problem.equals("kid") ? "unknown" : "k1")
                    .withIssuer(problem.equals("issuer") ? "other" : "trader-backend")
                    .withAudience(problem.equals("audience") ? "other" : "trader-services")
                    .withSubject("test-user").withClaim("userId", 1L).withClaim("role", "ROLE_USER")
                    .withClaim("nickName", "테스트").withClaim("token_use", "access")
                    .withIssuedAt(Date.from(now.plusSeconds(problem.equals("future") ? 600 : -1200)));
            if (!problem.equals("missing-exp")) builder.withExpiresAt(Date.from(now.plusSeconds(problem.equals("expired") ? -60 : 1800)));
            String value = builder.sign(Algorithm.RSA256((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate()));
            assertThatThrownBy(() -> provider.validateTokenOrThrow(value)).as(problem).isInstanceOf(BaseException.class);
        }
        String[] parts = access().split("\\.");
        String changed = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString("{\"userId\":99}".getBytes()) + "." + parts[2];
        assertThatThrownBy(() -> provider.validateTokenOrThrow(changed)).isInstanceOf(BaseException.class);
    }
    @Test void jwks_contains_only_public_fields() throws Exception {
        var result = new JwksController(ring).publicKeys();
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        var list = (List<?>) result.getBody().get("keys");
        assertThat(((Map<?, ?>) list.get(0)).keySet().stream().map(Object::toString).toList())
                .containsExactlyInAnyOrder("kty", "kid", "alg", "use", "n", "e");
    }
    @Test void key_configuration_missing_mismatch_and_wrong_format_fail_closed() throws Exception {
        assertThatThrownBy(() -> new JwtKeyRing("", "", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtKeyRing("other", temp.resolve("private.pem").toString(), temp.resolve("public").toString())).isInstanceOf(IllegalArgumentException.class);
        Files.writeString(temp.resolve("public/k1.pem"), "private-secret-marker");
        assertThatThrownBy(() -> new JwtKeyRing("k1", temp.resolve("private.pem").toString(), temp.resolve("public").toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("private-secret-marker");
    }
    @Test void rest_and_jwks_filter_contract() throws Exception {
        var filter = new JwtFilter(provider, mock(FormUserDetailService.class), new ObjectMapper());
        var request = new MockHttpServletRequest("GET", "/api/private");
        request.setCookies(new Cookie("accessToken", access()));
        var response = new MockHttpServletResponse(); var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isNotNull();
        var bad = new MockHttpServletRequest("GET", "/api/private");
        bad.setCookies(new Cookie("accessToken", refresh()));
        var rejected = new MockHttpServletResponse(); var stopped = new MockFilterChain();
        filter.doFilter(bad, rejected, stopped);
        assertThat(rejected.getStatus()).isEqualTo(401); assertThat(stopped.getRequest()).isNull();
        var publicChain = new MockFilterChain();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/auth/jwks"), new MockHttpServletResponse(), publicChain);
        assertThat(publicChain.getRequest()).isNotNull();
    }
    @Test void refresh_endpoint_rejects_access_cookie_value() throws Exception {
        var controller = new loginController(provider, mock(AuthenticationManager.class), mock(UserService.class));
        var request = new MockHttpServletRequest(); request.setCookies(new Cookie("refreshToken", access()));
        assertThatThrownBy(() -> controller.refreshJwt(request, new MockHttpServletResponse())).isInstanceOf(BaseException.class);
        request.setCookies(new Cookie("refreshToken", refresh()));
        var response = new MockHttpServletResponse();
        assertThat(controller.refreshJwt(request, response).getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
    }
    @Test void websocket_rejects_refresh_and_accepts_access() throws Exception {
        var interceptor = new JwtHandshakeInterceptor(provider, mock(UserDetailsService.class));
        for (boolean valid : List.of(true, false)) {
            var req = new MockHttpServletRequest(); req.setCookies(new Cookie("accessToken", valid ? access() : refresh()));
            var res = new MockHttpServletResponse();
            assertThat(interceptor.beforeHandshake(new ServletServerHttpRequest(req), new ServletServerHttpResponse(res),
                    mock(org.springframework.web.socket.WebSocketHandler.class), new HashMap<>())).isEqualTo(valid);
        }
    }
    @Test void exports_synthetic_cross_language_fixture() throws Exception {
        var dir = Path.of("build", "auth-contract"); Files.createDirectories(dir);
        // 테스트가 생성한 키로 서명한 가짜 사용자 토큰만. 개인키는 쓰지 않는다.
        new ObjectMapper().writeValue(dir.resolve("java-fixture.json").toFile(),
                Map.of("jwks", ring.jwks(), "access", access(), "refresh", refresh()));
    }
}
