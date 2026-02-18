package com.example.trader.common.interceptor;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.UserTeamRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

import static org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE;

@Component
@RequiredArgsConstructor
public class TeamMemberInterceptor implements HandlerInterceptor {

    private final UserTeamRepository userTeamRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {

        if (!(handler instanceof HandlerMethod hm)) return true;

        // 메서드 또는 클래스에 어노테이션이 있는지 확인
        TeamMemberRequired ann = hm.getMethodAnnotation(TeamMemberRequired.class);
        if (ann == null) ann = hm.getBeanType().getAnnotation(TeamMemberRequired.class);
        if (ann == null) return true;

        // teamId 꺼내기
        Map<String, String> uriVars =
                (Map<String, String>) request.getAttribute(URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (uriVars == null) throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);

        String teamIdStr = uriVars.get(ann.teamIdVar());
        if (teamIdStr == null) throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);

        Long teamId;
        try {
            teamId = Long.parseLong(teamIdStr);
        } catch (NumberFormatException e) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        // 현재 로그인 유저 id 가져오기 (너의 UserContext 기준)
        Long userId = extractUserId();
        if (userId == null) throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);

        // 멤버 여부 검증
        boolean isMember = userTeamRepository.existsByTeamIdAndUserId(teamId, userId);
        if (!isMember) {
            throw new BaseException(BaseResponseStatus.FAIL_AUTHENTICATE);
        }

        return true;
    }

    private Long extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;

        // 너가 쓰는 @AuthenticationPrincipal UserContext 가 principal로 들어온다는 가정
        if (auth.getPrincipal() instanceof com.example.trader.security.details.UserContext uc) {
            return uc.getUserDto().getId();
        }
        return null;
    }
}
