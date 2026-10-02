package com.example.trader.ai;

import com.example.trader.security.service.SecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** 상시 백엔드의 가용성 경계. 사용자 요청을 AWS 자격 증명/임의 URL로 바꾸지 않는다. */
@RestController
@RequestMapping("/api/ai")
@ConditionalOnProperty(name="ai.internal.enabled",havingValue="true")
public class AiAvailabilityController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final URI readiness;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private volatile long checkedAt;
    private volatile boolean ready;

    public AiAvailabilityController(JdbcTemplate jdbc,ObjectMapper mapper,
            @Value("${ai.runtime.readiness-url:http://127.0.0.1:8091/ai-api/health/ready}") String url) {
        this.jdbc=jdbc; this.mapper=mapper; this.readiness=URI.create(url);
    }

    private synchronized boolean probe() {
        // polling은 GPU 사용 시각을 갱신하지 않는다. 요청마다 의존성을 두드리지도 않는다.
        long now=System.nanoTime();
        if(now-checkedAt<Duration.ofSeconds(5).toNanos()) return ready;
        try {
            var response=client.send(HttpRequest.newBuilder(readiness)
                    .timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.ofString());
            ready=response.statusCode()==200 && "ready".equals(mapper.readTree(response.body()).path("status").asText());
        } catch(Exception e) {
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
            ready=false;
        }
        checkedAt=System.nanoTime();
        return ready;
    }

    @GetMapping("/availability")
    public Map<String,Object> status() {
        Long userId=SecurityService.getAuthenticationUserId();
        boolean available=probe();
        boolean requested=Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM ai_wake_request WHERE user_id=? AND notified_at IS NULL)",
                Boolean.class,userId));
        // 로컬 구현은 실제 ASG 기동을 가장하지 않는다. 운영 제어 어댑터는 별도 배포 범위다.
        return Map.of("state",available?"READY":"OFF","wakeRequested",requested,
                "controlMode","MANUAL_LOCAL","notification",available && requested);
    }

    @PostMapping("/wake")
    public Map<String,Object> wake(HttpServletRequest request) {
        if("cross-site".equals(request.getHeader("Sec-Fetch-Site"))) throw new AiApiException(403,"CROSS_SITE_REJECTED");
        Long userId=SecurityService.getAuthenticationUserId();
        // 사용자별 한 행: 반복 클릭이 작업/알림 대상을 무한 증식시키지 않는다.
        jdbc.update("""
            INSERT INTO ai_wake_request(user_id) VALUES(?)
            ON CONFLICT(user_id) DO UPDATE SET requested_at=clock_timestamp(),notified_at=NULL
            WHERE ai_wake_request.notified_at IS NOT NULL
            """,userId);
        return status();
    }

    @PostMapping("/notifications/ack")
    public Map<String,Object> acknowledge() {
        jdbc.update("UPDATE ai_wake_request SET notified_at=clock_timestamp() WHERE user_id=?",
                    SecurityService.getAuthenticationUserId());
        return Map.of("acknowledged",true);
    }
}
