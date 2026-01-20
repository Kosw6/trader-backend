package com.example.trader.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // actuatorChain(@Order(0)) 다음, securityFilterChain(@Order(2)) 이전
public class ExceptionLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ExceptionLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(req, res);
        } catch (Exception ex) {
            String params = req.getParameterMap().keySet().stream().collect(Collectors.joining(","));
            StringBuilder headers = new StringBuilder();
            for (Enumeration<String> e = req.getHeaderNames(); e.hasMoreElements();) {
                String h = e.nextElement();
                if (!h.equalsIgnoreCase("authorization")) headers.append(h).append(",");
            }
            log.error("🔥 EX at {} {}?{} | params=[{}] | headers=[{}]",
                    req.getMethod(), req.getRequestURI(), req.getQueryString(), params, headers.toString(), ex);
            throw ex;
        }
    }
}
