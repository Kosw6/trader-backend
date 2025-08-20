package com.example.trader.security.oauth2;

import com.example.trader.security.provider.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {
//jwt토큰 발급해서 리다이렉트 하는 기능
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper; // 주입

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
                                        Authentication authentication) throws IOException {
        var principal = (DefaultOAuth2User) authentication.getPrincipal();
        Long userId = ((Number)principal.getAttributes().get("id")).longValue();
        String email = (String) principal.getAttributes().get("email");

        String accessToken = jwtTokenProvider.createAccessToken(userId.toString());
        String refreshToken = jwtTokenProvider.createRefreshToken(userId.toString(),accessToken);

//        rtService.store(userId, refreshToken); // Redis setex

        // 3. Refresh 토큰을 HttpOnly 쿠키에 저장 (클라이언트가 접근하지 못하게 하기 위함)
        ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false) // 개발 환경에서는 false
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Lax") // "Lax" 또는 "Strict"도 가능
                .build();
        res.addHeader("Set-Cookie", cookie.toString());
        Map<String, String> tokens = new HashMap<>();
        tokens.put("accessToken", accessToken);
        res.setStatus(HttpServletResponse.SC_OK);
        res.setCharacterEncoding("UTF-8");
        res.setContentType("application/json");
        res.sendRedirect("http://localhost:5173/login/success");
        var body = Map.of(
                "accessToken", accessToken
        );
        res.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
