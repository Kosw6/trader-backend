package com.example.trader.controller;

import com.example.trader.dto.LoginRequest;
import com.example.trader.dto.SingUpDto;
import com.example.trader.entity.Gender;
import com.example.trader.entity.Role;
import com.example.trader.entity.User;
import com.example.trader.security.details.UserContext;
import com.example.trader.security.provider.JwtTokenProvider;
import com.example.trader.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

//TODO:access,refresh토큰 발급할 부분
@RestController
@RequiredArgsConstructor
public class loginController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    @PostMapping("/api/login/signin")//로그인 기능
    public Map<String, String> login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) throws IOException {
        try {
            //빈으로 등록하면서 커스텀 Provider을 제공한 AuthenticationManager로 유저 정보를 검증함
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getLoginId(), loginRequest.getPassword())
            );
            //유저ID로 토큰발행 -> 이렇게 하면 회원가입 정보가 없는 oauth2도 사용가능
            UserContext userContext= (UserContext) authentication.getPrincipal();
            Long userId = userContext.getUserDto().getId();
            // 2. 유저아이디로 Access 토큰 및 Refresh 토큰 생성
            String accessToken = jwtTokenProvider.createAccessToken(userId.toString());
            String refreshToken = jwtTokenProvider.createRefreshToken(userId.toString(),accessToken);

            // 3. Refresh 토큰을 HttpOnly 쿠키에 저장 (클라이언트가 접근하지 못하게 하기 위함)
            Cookie refreshTokenCookie = new Cookie("refreshToken", refreshToken);
            refreshTokenCookie.setHttpOnly(true);
            refreshTokenCookie.setSecure(true); // HTTPS로만 전송 가능
            refreshTokenCookie.setPath("/");    // 모든 경로에서 접근 가능
            refreshTokenCookie.setMaxAge(7 * 24 * 60 * 60); // 7일간 유효
            response.addCookie(refreshTokenCookie);

            // 4. Access 토큰을 응답 바디에 담아서 반환
            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", accessToken);
            return tokens;  // Access 토큰은 클라이언트 측에서 로컬 스토리지 또는 메모리에 저장하여 사용
        } catch (AuthenticationException e) {
            // 인증 실패 시 401 Unauthorized 응답
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    //todo:회원가입시 201응답을 받았으면 프론트에서 로그인 페이지로 리다이렉트시키기
    @PostMapping("/api/login/signup")
    public ResponseEntity join(@RequestBody SingUpDto singUpDto){
        User user = User.builder()
                .nick_name(singUpDto.nickName())
                .loginId(singUpDto.loginId())
                .password(singUpDto.password())
                .age(singUpDto.age())
                .email(singUpDto.email())
                .gender(Gender.ofGender(singUpDto.gender()))
                .username(singUpDto.userName())
                .role(Role.USER)
                .build();
        userService.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }


}
