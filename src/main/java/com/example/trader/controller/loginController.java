package com.example.trader.controller;

import com.example.trader.dto.LoginRequest;
import com.example.trader.dto.SingUpDto;
import com.example.trader.entity.Gender;
import com.example.trader.entity.Role;
import com.example.trader.entity.User;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.security.details.UserContext;
import com.example.trader.security.provider.JwtTokenProvider;
import com.example.trader.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

//TODO:access,refresh토큰 발급할 부분
@Slf4j
@Tag(name = "Login API", description = "로그인,회원가입 관련 데이터 API")
@RestController
@RequiredArgsConstructor
public class loginController {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    @Operation(
            summary = "로그인"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "JWT 예시",
                                    value = """
                {
                  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                }
                """
                            )
                    )),
//            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음",
//                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/api/login/signin")//로그인 기능
    public ResponseEntity login(@RequestBody LoginRequest loginRequest, HttpServletResponse response) throws IOException {
        try {
            //빈으로 등록하면서 커스텀 Provider을 제공한 AuthenticationManager로 유저 정보를 검증함
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getLoginId(), loginRequest.getPassword())
            );
            //유저ID로 토큰발행 -> 이렇게 하면 회원가입 정보가 없는 oauth2도 사용가능
            UserContext userContext= (UserContext) authentication.getPrincipal();
            String loginId = userContext.getUserDto().getLoginId();
            // 2. 유저아이디로 Access 토큰 및 Refresh 토큰 생성
            String accessToken = jwtTokenProvider.createAccessToken(loginId);
            String refreshToken = jwtTokenProvider.createRefreshToken(loginId,accessToken);

            // 3. Refresh 토큰을 HttpOnly 쿠키에 저장 (클라이언트가 접근하지 못하게 하기 위함)
            ResponseCookie cookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(false) // 개발 환경에서는 false
                    .path("/")
                    .maxAge(7 * 24 * 60 * 60)
                    .sameSite("Lax") // "Lax" 또는 "Strict"도 가능
                    .build();
            response.addHeader("Set-Cookie", cookie.toString());

            // 4. Access 토큰을 응답 바디에 담아서 반환
            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", accessToken);
            return ResponseEntity.ok().body(tokens);
              // Access 토큰은 클라이언트 측에서 로컬 스토리지 또는 메모리에 저장하여 사용
        } catch (AuthenticationException e) {
            // 인증 실패 시 401 Unauthorized 응답
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedEncodingException();
        }
    }
    //todo:회원가입시 201응답을 받았으면 프론트에서 로그인 페이지로 리다이렉트시키기
    @Operation(
            summary = "회원가입"
    )
    @ApiResponse(responseCode = "201", description = "성공")
    @PostMapping("/api/login/signup")
    public ResponseEntity join(@RequestBody SingUpDto singUpDto){
        User user = User.builder()
                .nickName(singUpDto.nickName())
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

    @GetMapping("/api/login/refresh")
    public ResponseEntity refreshJwt(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {
//        log.info("refresh catch");
//        log.info("request.getCookies():",request.getCookies());
            if(request.getCookies()!=null){
            for (Cookie cookie : request.getCookies()) {
//                log.info(cookie.getName());
                if("refreshToken".equals(cookie.getName())){
                    //토큰 확인하고 복호화
                    String token = cookie.getValue();
                    String loginId = jwtTokenProvider.getTokenInfo(token);
                    // 토큰이 존재하고 유효하면 사용자 정보를 SecurityContext에 설정 && 토큰유지기한이 유효한지 체크
                    if (loginId!=null && jwtTokenProvider.validateToken(token)!=null) {
                        // 2. 유저아이디로 Access 토큰 및 Refresh 토큰 생성
                        String accessToken = jwtTokenProvider.createAccessToken(loginId);
                        String refreshToken = jwtTokenProvider.createRefreshToken(loginId,accessToken);
                        Cookie refreshTokenCookie = new Cookie("refreshToken", refreshToken);
                        refreshTokenCookie.setHttpOnly(true);
                        refreshTokenCookie.setSecure(true); // HTTPS로만 전송 가능
                        refreshTokenCookie.setPath("/");    // 모든 경로에서 접근 가능
                        refreshTokenCookie.setMaxAge(7 * 24 * 60 * 60); // 7일간 유효
                        response.addCookie(refreshTokenCookie);
                        Map<String, String> tokens = new HashMap<>();
                        tokens.put("accessToken", accessToken);
                        return ResponseEntity.ok().body(tokens);
                    }
                }
            }

        }
        throw new BaseException(BaseResponseStatus.FAIL_TOKEN_AUTHORIZATION);
    }


}
