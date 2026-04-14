package com.example.trader.security.oauth2;

import com.example.trader.security.provider.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;

    // 환경변수 APP_OAUTH2_REDIRECT_URL / app.oauth2.redirect-url 로 주입
    @Value("${app.oauth2.redirect-url:http://localhost:5173/login/success}")
    private String redirectUrl;

    // 환경변수 APP_COOKIE_SECURE / app.cookie.secure 로 주입 (HTTPS 배포 시 true)
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
                                        Authentication authentication) throws IOException {

        DefaultOAuth2User principal = (DefaultOAuth2User) authentication.getPrincipal();

        Long userId = ((Number) principal.getAttributes().get("id")).longValue();
        String email = (String) principal.getAttributes().get("email");

        String nickName = (String) principal.getAttributes().get("name");
        if (nickName == null) {
            // CustomOAuth2UserService에서 name을 채워주지 못한 경우 userId 기반 fallback
            nickName = "user_" + userId;
        }

        String loginId = email;

        // authentication.getAuthorities()에서 role을 추출해 하드코딩 제거
        String roleName = authentication.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("ROLE_USER");

        String accessToken = jwtTokenProvider.createAccessToken(
                userId,
                loginId,
                roleName,
                nickName
        );

        String refreshToken = jwtTokenProvider.createRefreshToken(
                userId,
                loginId,
                roleName,
                nickName
        );

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(60 * 30)
                .sameSite("Lax")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Lax")
                .build();

        res.addHeader("Set-Cookie", accessCookie.toString());
        res.addHeader("Set-Cookie", refreshCookie.toString());

        res.sendRedirect(redirectUrl);
    }
}