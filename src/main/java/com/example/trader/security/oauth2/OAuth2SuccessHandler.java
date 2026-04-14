package com.example.trader.security.oauth2;

import com.example.trader.security.provider.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res,
                                        Authentication authentication) throws IOException {

        DefaultOAuth2User principal = (DefaultOAuth2User) authentication.getPrincipal();

        Long userId = ((Number) principal.getAttributes().get("id")).longValue();
        String email = (String) principal.getAttributes().get("email");

        // 공급자별로 다를 수 있음. 구글/카카오 attribute 구조 확인 필요
        String nickName = (String) principal.getAttributes().get("name");
        if (nickName == null) {
            nickName = email;
        }

        String loginId = email; // OAuth는 email을 subject/loginId 대용으로 사용
        String roleName = "ROLE_USER";

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
                .secure(false) // 배포 HTTPS면 true
                .path("/")
                .maxAge(60 * 30)
                .sameSite("Lax")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(false) // 배포 HTTPS면 true
                .path("/")
                .maxAge(7 * 24 * 60 * 60)
                .sameSite("Lax")
                .build();

        res.addHeader("Set-Cookie", accessCookie.toString());
        res.addHeader("Set-Cookie", refreshCookie.toString());

        // JSON 바디 쓰지 말고 redirect만
        res.sendRedirect("http://localhost:5173/login/success");
    }
}