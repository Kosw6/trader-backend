package com.example.trader.security.provider;

import com.example.trader.security.details.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

//일반적인 로그인 인증시
@Component
@RequiredArgsConstructor
public class FormAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String loginId = authentication.getName();
        String password = (String)authentication.getCredentials();
        //가져온 유저의 정보로 비밀번호 매칭
        UserContext userContext = (UserContext) userDetailsService.loadUserByUsername(loginId);
        //TODO:비밀번호 암호화등 처리하기
        //요청 id,password와 서버에 저장된 id,password비교
        if(!password.equals(userContext.getPassword())){
            //TODO:클라이언트가 따로 처리
            throw new BadCredentialsException("일치하지 않은 비밀번호입니다.");
        }

        return new UsernamePasswordAuthenticationToken(userContext, null, userContext.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {//넘어오는 authentication을 Provider가 처리할 수 있는지 검증하는 메서드
        return authentication.isAssignableFrom(UsernamePasswordAuthenticationToken.class);//유저네임패스워드형식을 지원
    }
}
