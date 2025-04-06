package com.example.trader.security.service;

import com.example.trader.entity.Role;
import com.example.trader.entity.User;
import com.example.trader.repository.UserRepository;
import com.example.trader.security.info.GoogleUserInfo;
import com.example.trader.security.info.KakaoUserInfo;
import com.example.trader.security.info.NaverUserInfo;
import com.example.trader.security.info.OAuth2UserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {


    private final UserRepository userRepository;
    OAuth2UserInfo userInfo;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        Map properties = (Map) oauth2User.getAttributes().get("properties");
        System.out.println(properties);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        if ("google".equals(provider)) {
            userInfo = new GoogleUserInfo(oauth2User.getAttributes());
        } else if ("kakao".equals(provider)) {
            userInfo = new KakaoUserInfo(oauth2User.getAttributes());
        } else if ("naver".equals(provider)) {
            userInfo = new NaverUserInfo(oauth2User.getAttributes());
        }
        String providerId = userInfo.getProviderId();
        String email = userInfo.getEmail();

        Optional<User> userOptional = userRepository.findByEmailAndProviderId(email, providerId);

        User user;
        //카카오,구글등 같은 이름이어도 다른 사이트일 수도 있으므로, + 각 oath2제공업체에서 유일키로 구성을 해야함
        if (userOptional.isPresent() && userOptional.get().getProviderId() == providerId) {
            userOptional.get().getId();
            // 로그인 처리
        } else {
            // 회원가입 처리
            processRegistration(email, providerId);
        }

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                properties,
                "nickname");
    }

//    private User processLogin(User user, String provider, String providerId) {
//        // 로그인 처리 로직
//        // 예: 마지막 로그인 시간 업데이트
//        return userRepository.save(user);
//    }
    //todo:구글과 네이버 아이디 계정으로 oath2로그인 구현하기 성별,나이,닉네임등을 추가로 정하는 프론트 템플릿을 만들어야 하는가
    //-> 컨트롤러로 만들고 oath2회원가입/로그인 후에 성별,생년월일이 없는 유저의 경우 리다이렉트 시키기
    private User processRegistration(String email, String provider) {
        User user = null;
        // 회원가입 처리 로직???????????????????????
        if(provider == "kakao") {
//            user = User.builder()
//                    .email(email)
//                    .password("test")
//                    .loginId(null)
//                    .username(null)
//                    .role(Role.USER)
//                    .gender(null).build();
        }else if(provider == "google"){

        }else if(provider == "naver"){

        }else{
            throw new IllegalArgumentException("존재하지 않는 oauth2 provider : " +provider);
        }

        return userRepository.save(user);
    }

}
