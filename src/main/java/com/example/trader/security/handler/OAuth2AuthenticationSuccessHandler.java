package com.example.trader.security.handler;

import com.example.trader.security.provider.JwtTokenProvider;
import com.example.trader.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        //TODO:어트리부트 수정
        String email = oAuth2User.getAttribute("nickname");
        String providerId = oAuth2User.getAttribute("sub");
//        userService.findUserByEmailAndProviderId(email,)

//        String token = jwtTokenProvider.createAccessToken(userId.toString());

//        response.addHeader("Authorization", "Bearer " + token);

        // 클라이언트로 리다이렉트 또는 JSON 응답
        getRedirectStrategy().sendRedirect(request, response, "/oauth2-success");
    }
}

