package com.example.trader.security.config;

import com.example.trader.security.entrypoint.CustomAuthenticationEntryPoint;
import com.example.trader.security.filter.JwtFilter;
import com.example.trader.security.oauth2.CustomOAuth2UserService;
import com.example.trader.security.oauth2.OAuth2SuccessHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Slf4j
@EnableWebSecurity(debug = true)
@EnableMethodSecurity(prePostEnabled = true)
@Configuration//빈설정파일
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationProvider authenticationProvider;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final AccessDeniedHandler customAccessDeniedHandler;
    private final JwtFilter jwtFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
//    private final CorsConfigurationSource corsConfigurationSource;

    // AuthenticationManager 빈을 설정하여 인증을 처리
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .authenticationProvider(authenticationProvider)//커스텀 Provider사용
//                .passwordEncoder(passwordEncoder)
                .build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173")); // 허용할 프론트 주소
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE","PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With","Access-Control-Request-Method","Access-Control-Request-Headers","Access-Control-Allow-Origin","Access-Control-Allow-Credentials"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // 캐시 시간(초)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }


    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository() {
        return new HttpSessionOAuth2AuthorizationRequestRepository();
    }
    // 0) Actuator만 전부 허용 (Prometheus 스크랩용)
    @Bean
    @Order(0)
    SecurityFilterChain actuatorChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(new AntPathRequestMatcher("/actuator/**")) // ← 명시 매칭
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .requestCache(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
            http.cors(cors->cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            //TODO:swagger보안처리 배포전에 해두기
                            .requestMatchers(
                                    "/actuator/**",
                                    "/error",
                                    "/swagger-ui/**",
                                    "/v3/api-docs",
                                    "/v3/api-docs/**",
                                    "/swagger-resources/**",
                                    "/webjars/**",
                                    "/oauth2/**",
                                    "/login/oauth2/**",
                                    "/api/login/**",
                                    "/api/oauth2/**",
                                    "/login/oauth2/code/**").permitAll()
                            .anyRequest().authenticated())
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(exception -> {
                        exception.authenticationEntryPoint(authenticationEntryPoint);
                        exception.accessDeniedHandler(customAccessDeniedHandler);
                    })
                    // 1) OAuth2 로그인(성공/실패 핸들러 + 사용자 정보 매핑)
                    .oauth2Login(o -> o
                            .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                            .successHandler(oAuth2SuccessHandler)
                            .failureHandler((request, response, exception) -> {
                                log.error("OAuth2 로그인 실패: {}", exception.getMessage(), exception);
                                response.sendRedirect("/login?oauth2_error"); // 프론트 에러 페이지로 보내든지
                            })
                    );
            return http.build();
    }


}
