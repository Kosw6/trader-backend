package com.example.trader.security.filter;

import com.example.trader.security.provider.JwtTokenProvider;
import com.example.trader.security.service.FormUserDetailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    //URL 패턴 매칭&&*와 같은 와일드 카드 사용가능,
//    private final AntPathMatcher antPathMatcher = new AntPathMatcher();
    private final JwtTokenProvider jwtTokenProvider;
    private final FormUserDetailService userDetailService;//loadUserByUsername로 사용자 정보 로드
    private final ObjectMapper objectMapper;

    public JwtFilter(JwtTokenProvider jwtTokenProvider, FormUserDetailService userDetailService, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailService = userDetailService;
        this.objectMapper = objectMapper;
    }

    //쓰레드로컬에 저장된 authentication을 가져옴(기본 컨텍스트홀더의 세팅값은 쓰레드로컬)X
    //세션설정을 꺼버렸으므로 쓰레드로컬에 authenticaition이 저장되지 않음; 그래도 이전 필터에서 인증은 성공했으므로
    //Provider을 이용하여 사용자 정보를 꺼내서 authenticaion객체를 생성하고 ThreadLocal에 직접 저장해주어야 한다.
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        //TODO:배포시에 swagger보안처리
        if (path.startsWith("/api/login")||path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-resources")
                || path.startsWith("/webjars")) {
            filterChain.doFilter(request, response); // 로그인 관련 요청은 필터 통과
            return;
        }
        //토큰 확인하고 복호화
        String token = jwtTokenProvider.resolveToken(request);
        // 토큰이 존재하고 유효하면 사용자 정보를 SecurityContext에 설정 && 토큰유지기한이 유효한지 체크
        if (token != null && jwtTokenProvider.validateToken(token) != null) {
            // JWT에서 사용자 인증 정보를 가져옴
            Authentication authentication = jwtTokenProvider.getAuthentication(token, userDetailService);
            // SecurityContextHolder에 인증 정보 설정 (요청에 대해 일시적으로 유효함)
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("Context에 Authenticaion저장");
        } else {
            //토큰이 없거나 유지기한이 유효하지 않을떄
            // TODO:리프레쉬 토큰 확인하고 안되면 따로 처리해주기 예를들어 에러응답을 반환하거나 401?
            handleInvalidToken(response, "accessToken인증오류");
        }

        try {
            log.info("JWT필터->다음 필터로");
            // 다음 필터로 요청을 넘김->userNamePasswordAuthenticaionFilter
            System.out.println(SecurityContextHolder.getContext().getAuthentication());
            log.info("Authentication set in SecurityContext: {}", SecurityContextHolder.getContext().getAuthentication());
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw e;
        }
        finally {
            log.info("JWT필터 SecurityContextHolder.clearContext() 작동");
            // SecurityContext 초기화 (요청이 끝난 후)
            SecurityContextHolder.clearContext();
        }

    }

    //응답 상태코드 변경
    private void handleInvalidToken(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new HashMap<>();
        body.put("error", "Unauthorized");
        body.put("message", message);
        body.put("status", HttpStatus.UNAUTHORIZED.value());

        objectMapper.writeValue(response.getWriter(), body);
    }
}
