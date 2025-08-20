package com.example.trader.security.oauth2;

import com.example.trader.entity.User;
import com.example.trader.repository.UserRepository;
import com.example.trader.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest req) throws OAuth2AuthenticationException {
        DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
        OAuth2User oauthUser = delegate.loadUser(req);

        String registrationId = req.getClientRegistration().getRegistrationId(); // google/kakao
        Map<String, Object> attrs = oauthUser.getAttributes();

        String providerUserId;
        String email = null;
        String name = null;

        if ("google".equals(registrationId)) {
            providerUserId = (String) attrs.get("sub");
            email = (String) attrs.get("email");
            name  = (String) attrs.get("name");
        } else if ("kakao".equals(registrationId)) {
            providerUserId = String.valueOf(attrs.get("id"));
            Map<String, Object> account = (Map<String, Object>) attrs.get("kakao_account");
            Map<String, Object> profile = account == null ? null : (Map<String, Object>) account.get("profile");
            //TODO:비즈앱 신청하고 수정
            email = account == null ? "null" : (String) account.get("email");
            if(email==null){
                email = "test@naver.com";
            }
            name  = profile == null ? "null" : (String) profile.get("nickname");
        } else {
            throw new OAuth2AuthenticationException("Unsupported provider");
        }

        // upsert 사용자
        User user = userService.upsertOAuthUser(registrationId, providerUserId, email, name);

        // ROLE_USER 기본 부여
        List authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        Map userAttributes = Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "name", user.getUsername()
        );
        return new DefaultOAuth2User(authorities, userAttributes, "email");
    }
}
