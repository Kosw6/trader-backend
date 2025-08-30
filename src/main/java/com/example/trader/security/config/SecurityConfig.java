package com.example.trader.security.config;

import com.example.trader.security.entrypoint.CustomAuthenticationEntryPoint;
import com.example.trader.security.filter.JwtFilter;
import com.example.trader.security.oauth2.CustomOAuth2UserService;
import com.example.trader.security.oauth2.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With","Access-Control-Request-Method","Access-Control-Request-Headers","Access-Control-Allow-Origin","Access-Control-Allow-Credentials"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // 캐시 시간(초)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
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
    @Order(1)
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
                                    "/api/login/**",
                                    "/oauth2/**",
                                    "/login/oauth2/code/**").permitAll()
                            .anyRequest().authenticated())
                    //TODO:oauth2로그인 -> 앱 개발후 CI정보 제공 서비스에서 비즈앱 등록받아야 원하는 정보 받을 수 있음.
//                    .oauth2Login(oauth2 -> oauth2.userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService)))
                    //todo:jwt필터의 위치는? oauth2필터의 앞이여야 하는가
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
                    );
            return http.build();
    }
}
