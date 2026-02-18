package com.example.trader.security.entrypoint;

import com.example.trader.httpresponse.BaseResponseStatus;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        // 응답 코드 설정
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Unauthorized

        // 응답 헤더 설정
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        System.out.println(authException.getMessage());
//        response.setStatus(BaseResponseStatus.FAIL_AUTHENTICATE.getCode())
        response.sendError(BaseResponseStatus.FAIL_AUTHENTICATE.getCode(), authException.getMessage());
//        response.sendError(BaseResponseStatus.FAIL_AUTHENTICATE.getCode(), BaseResponseStatus.FAIL_AUTHENTICATE.getCode());
    }
}