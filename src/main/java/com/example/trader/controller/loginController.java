package com.example.trader.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
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
import java.util.HashMap;
import java.util.Map;

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
    @PostMapping("/api/login/signin")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest,
                                   HttpServletResponse response) throws IOException {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getLoginId(), loginRequest.getPassword())
            );

            UserContext userContext = (UserContext) authentication.getPrincipal();

            Long userId = userContext.getUserDto().getId();
            String loginId = userContext.getUserDto().getLoginId();
            String nickName = userContext.getUserDto().getNickName();
            String roleName = userContext.getAuthorities()
                    .iterator()
                    .next()
                    .getAuthority();

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

            // ✅ accessToken 쿠키
            ResponseCookie accessCookie = ResponseCookie.from("accessToken", accessToken)
                    .httpOnly(true)
                    .secure(false)          // 배포(HTTPS)면 true
                    .path("/")
                    .maxAge(60 * 30)        // 예: 30분
                    .sameSite("Lax")        // cross-site면 None 필요
                    .build();

            // ✅ refreshToken 쿠키
            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                    .httpOnly(true)
                    .secure(false)          // 배포(HTTPS)면 true
                    .path("/")
                    .maxAge(7 * 24 * 60 * 60)
                    .sameSite("Lax")
                    .build();

            response.addHeader("Set-Cookie", accessCookie.toString());
            response.addHeader("Set-Cookie", refreshCookie.toString());

            // ✅ 완전 쿠키 기반이면 바디는 비워도 됨(프론트가 토큰을 들고 있을 필요가 없음)
            return ResponseEntity.ok().build();

        } catch (AuthenticationException e) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        } catch (UnsupportedEncodingException e) {
            throw new UnsupportedEncodingException();
        }
    }
    @Operation(
            summary = "회원가입"
    )
    @ApiResponse(responseCode = "201", description = "성공")
    @PostMapping("/api/login/signup")
    public ResponseEntity<?> join(@RequestBody SingUpDto singUpDto){
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
    public ResponseEntity<?> refreshJwt(HttpServletRequest request, HttpServletResponse response)
            throws UnsupportedEncodingException {

        String refreshToken = null;

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("refreshToken".equals(c.getName())) {
                    refreshToken = c.getValue();
                    break;
                }
            }
        }

        if (refreshToken == null) {
            throw new BaseException(BaseResponseStatus.FAIL_TOKEN_AUTHORIZATION);
        }

        try {
            DecodedJWT jwt = jwtTokenProvider.validateTokenOrThrow(refreshToken);

            Long userId = jwt.getClaim("userId").asLong();
            String loginId = jwt.getSubject();
            String roleName = jwt.getClaim("role").asString();
            String nickName = jwt.getClaim("nickName").asString();

            if (userId == null || loginId == null || roleName == null || nickName == null) {
                throw new BaseException(BaseResponseStatus.INVALID_JWT_TOKEN);
            }

            String newAccess = jwtTokenProvider.createAccessToken(
                    userId,
                    loginId,
                    roleName,
                    nickName
            );

            String newRefresh = jwtTokenProvider.createRefreshToken(
                    userId,
                    loginId,
                    roleName,
                    nickName
            );

            ResponseCookie accessCookie = ResponseCookie.from("accessToken", newAccess)
                    .httpOnly(true)
                    .secure(false)     // 배포면 true
                    .path("/")
                    .maxAge(60 * 30)
                    .sameSite("Lax")
                    .build();

            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", newRefresh)
                    .httpOnly(true)
                    .secure(false)     // 배포면 true
                    .path("/")
                    .maxAge(7 * 24 * 60 * 60)
                    .sameSite("Lax")
                    .build();

            response.addHeader("Set-Cookie", accessCookie.toString());
            response.addHeader("Set-Cookie", refreshCookie.toString());

            return ResponseEntity.ok().build();

        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            throw new BaseException(BaseResponseStatus.FAIL_TOKEN_AUTHORIZATION);
        }
    }


    @PostMapping("/api/login/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {

        // accessToken 삭제
        ResponseCookie deleteAccess = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(false) // 배포시 true
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        // refreshToken 삭제
        ResponseCookie deleteRefresh = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(false) // 배포시 true
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        response.addHeader("Set-Cookie", deleteAccess.toString());
        response.addHeader("Set-Cookie", deleteRefresh.toString());

        return ResponseEntity.ok().build();
    }
}
