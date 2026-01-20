package com.example.trader.security.provider;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.trader.entity.User;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.Date;

@Slf4j
@Service
public class JwtTokenProvider {

    public final String secret = "~cN-T:#7T(1Ocfc7{";//시그니쳐키
    //TODO:나중에 최종 빌드할때는 시간 줄이기
    public final int accessTokenExpMinutes = 30;//엑세스 토큰
    public final int refreshTokenExpMinutes = 100;//리프레쉬 토큰

    private final UserService userService;
    private final UserDetailsService userDetailsService;
    public JwtTokenProvider(UserDetailsService userDetailsService,UserService userService) {
        this.userService = userService;
        this.userDetailsService = userDetailsService;
    }

    //유저 이름을 jwt토큰에 담아서 생성하고 반환, 컨트롤러에서 헤더에 담아서 반환
    //ex) headers.set("Authorization", "Bearer " + token);형식으로
    //todo:RoleClaim넣기:.withClaim("r","a")->r는 role, a는 admin,u는 user
    public String createAccessToken(String loginId) throws UnsupportedEncodingException {
        return JWT.create()
                .withSubject(loginId)
                .withIssuer("auth0")
                .withIssuedAt(new Date(System.currentTimeMillis()))
                .withExpiresAt(new Date(System.currentTimeMillis() + (60000 * accessTokenExpMinutes)))//10분
                .sign(Algorithm.HMAC256(secret));//알고리즘으로 서명후 반환
    }
    public String createAccessTokenByEmail(String email) throws UnsupportedEncodingException {
        return JWT.create()
                .withSubject(email)
                .withIssuer("auth0")
                .withIssuedAt(new Date(System.currentTimeMillis()))
                .withExpiresAt(new Date(System.currentTimeMillis() + (60000 * accessTokenExpMinutes)))//10분
                .sign(Algorithm.HMAC256(secret));//알고리즘으로 서명후 반환
    }

    //refreshToken발급하는 메서드
    public String createRefreshToken(String loginId, String accessToken) throws UnsupportedEncodingException{
        return JWT.create()
                .withSubject(loginId)
                .withIssuer("auth0")
                .withClaim("accessToken",accessToken)
                .withIssuedAt(new Date(System.currentTimeMillis()))
                .withExpiresAt(new Date(System.currentTimeMillis() + (60000 * refreshTokenExpMinutes)))//60초 * 1000밀리초 = 60000밀리초:1분*100분
                .sign(Algorithm.HMAC256(secret));//알고리즘으로 서명후 반환
    }
    public String getTokenInfo(String receivedToken) throws UnsupportedEncodingException {
        //비밀키를 받아서 알고리즘으로 복호화, 만약 전달받은 토큰이 알고리즘과 비밀키가 일치하지 않으면 예외반환하고
        //만약 일치하면 복호화한 유저ID반환
        //만료시간 비교하고 만약 만료되었으면 리프레쉬 토큰을 확인하고
        DecodedJWT decodedJWT = validateToken(receivedToken);
        if(decodedJWT.getSubject() != null){
            if(new Date(System.currentTimeMillis()).before(decodedJWT.getExpiresAt())) {//토큰만료시간 이전이냐
                return decodedJWT.getSubject();
            }else{//토큰이 만료되었을때 발생->호출하는 쪽에서 getRefreshTokenInfo메서드로 확인하고 AccessToken발급
                throw new BaseException(BaseResponseStatus.ACCESS_TOKEN_EXPIRED);
            }
        }
        throw new BaseException(BaseResponseStatus.INVALID_JWT_TOKEN);
    }
    public String getTokenSubject(DecodedJWT jwt) {
        String sub = jwt.getSubject();
        if (sub == null) {
            throw new BaseException(BaseResponseStatus.INVALID_JWT_TOKEN);
        }
        return sub;
    }


    // 토큰 유효성 검사-복호화하고, 시간확인
    public DecodedJWT validateToken(String receivedToken) {
//        log.info("run validation");
        try {
            DecodedJWT verify = JWT.require(Algorithm.HMAC256(secret))
                    .build().verify(receivedToken);
            if(new Date(System.currentTimeMillis()).before(verify.getExpiresAt())) {//토큰만료시간 이전이냐
                return verify;
            }
            return null;//호출하는 쪽에서 널값 체크해서 처리
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public DecodedJWT validateTokenOrThrow(String token) {
        try {
            DecodedJWT jwt = JWT.require(Algorithm.HMAC256(secret))
                    .build()
                    .verify(token); // 여기서 서명/형식 검증

            Date exp = jwt.getExpiresAt();
            if (exp == null) {
                throw new BaseException(BaseResponseStatus.INVALID_JWT_TOKEN);
            }
            if (System.currentTimeMillis() > exp.getTime()) {
                throw new BaseException(BaseResponseStatus.ACCESS_TOKEN_EXPIRED);
            }
            return jwt;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("JWT validation failed: {}", e.getMessage());
            throw new BaseException(BaseResponseStatus.INVALID_JWT_TOKEN);
        }
    }

    // 요청에서 JWT 토큰 추출
    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");//accessToken는 헤더에 담겨서 [refreshToken은 쿠키로]
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

     //Authentication 객체 생성
    //여기서 검증X + loginId 레포지토리 검색 대신 토큰에 담긴 정보 가져오기 -> 사용자 정보는 UserDetailsService를 Override한 메서드로 DB조회
//    public Authentication getAuthentication(String token, UserDetailsService userDetailsService) throws UnsupportedEncodingException {
//        String userId = getTokenInfo(token);//유저아이디 받아아서 로그인 아이디로 변환
//        Long id = Long.parseLong(userId);
//        String loginId = userService.findUserByUserId(id).getLoginId();
//        UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);
//        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
//    }
     public Authentication getAuthentication(DecodedJWT jwt, UserDetailsService uds) throws UnsupportedEncodingException {
         String loginId = jwt.getSubject();
         UserDetails userDetails = uds.loadUserByUsername(loginId);
         return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
     }
}