package com.example.trader.config;

import com.example.trader.common.interceptor.TeamMemberInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TeamMemberInterceptor teamMemberInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(teamMemberInterceptor)
                .addPathPatterns("/api/teams/**"); // 팀 API 전체에 걸어두고, 어노테이션으로 실제 적용 제어
    }
}
