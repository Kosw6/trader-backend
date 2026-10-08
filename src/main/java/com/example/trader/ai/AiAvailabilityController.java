package com.example.trader.ai;

import com.example.trader.security.service.SecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
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
    private final AiInstanceStatus instanceStatus;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private volatile long checkedAt;
    private Map<String,Object> runtimeState=Map.of("state","UNAVAILABLE");

    public AiAvailabilityController(JdbcTemplate jdbc,ObjectMapper mapper,
            @Value("${ai.runtime.readiness-url:http://127.0.0.1:8091/ai-api/health/ready}") String url,
            AiInstanceStatus instanceStatus) {
        this.jdbc=jdbc; this.mapper=mapper; this.readiness=URI.create(url);
        this.instanceStatus=instanceStatus;
    }

    private synchronized Map<String,Object> probe() {
        // polling은 GPU 사용 시각을 갱신하지 않는다. 요청마다 의존성을 두드리지도 않는다.
        long now=System.nanoTime();
        if(checkedAt != 0 && now-checkedAt<Duration.ofSeconds(5).toNanos()) return runtimeState;
        var infrastructure=instanceStatus.read();
        runtimeState=resolve(infrastructure, () -> readRuntime());
        checkedAt=System.nanoTime();
        return runtimeState;
    }

    static Map<String,Object> resolve(AiInstanceStatus.Snapshot instance,
            java.util.function.Supplier<Map<String,Object>> runtime) {
        // EC2가 꺼져 있으면 죽은 private endpoint를 호출하거나 READY 캐시를 신뢰하지 않는다.
        return switch(instance.state()) {
            case STOPPED -> Map.of("state","STOPPED");
            case STARTING -> Map.of("state","STARTING");
            case UNKNOWN -> Map.of("state","UNAVAILABLE");
            case DISABLED -> runtime.get();
            case RUNNING -> {
                var result=runtime.get();
                yield instance.bootGrace() && "UNAVAILABLE".equals(result.get("state"))
                        ? Map.of("state","STARTING") : result;
            }
        };
    }

    private Map<String,Object> readRuntime() {
        try {
            var response=client.send(HttpRequest.newBuilder(readiness)
                    .timeout(Duration.ofSeconds(3)).GET().build(),HttpResponse.BodyHandlers.ofString());
            return decodeState(mapper,response.statusCode(),response.body());
        } catch(Exception e) {
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
            // 연결 실패는 EC2 정지의 증거가 아니다. 오래된 READY도 유지하지 않는다.
            return Map.of("state","UNAVAILABLE");
        }
    }

    static Map<String,Object> decodeState(ObjectMapper mapper,int status,String body) throws Exception {
        var json=mapper.readTree(body);
        String state=json.path("state").asText();
        if(status==200 && "READY".equals(state) && "ready".equals(json.path("status").asText()))
            return Map.of("state","READY");
        if(status==503 && "not_ready".equals(json.path("status").asText()) &&
                Set.of("STOPPED","STARTING","WARMING","UNAVAILABLE").contains(state))
            return Map.of("state",state);
        return Map.of("state","UNAVAILABLE");
    }

    @GetMapping("/availability")
    public Map<String,Object> status() {
        Long userId=SecurityService.getAuthenticationUserId();
        var snapshot=probe();
        boolean available="READY".equals(snapshot.get("state"));
        boolean requested=Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM ai_wake_request WHERE user_id=? AND notified_at IS NULL)",
                Boolean.class,userId));
        // 로컬 구현은 실제 ASG 기동을 가장하지 않는다. 운영 제어 어댑터는 별도 배포 범위다.
        return Map.of("state",snapshot.get("state"),"wakeRequested",requested,
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
