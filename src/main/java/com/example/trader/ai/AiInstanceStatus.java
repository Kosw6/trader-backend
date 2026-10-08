package com.example.trader.ai;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;

/** 관리자가 지정한 생성 서버 하나만 읽는다. 요청자가 대상이나 AWS 명령을 지정하지 않는다. */
@Component
@ConditionalOnProperty(name="ai.internal.enabled", havingValue="true")
public class AiInstanceStatus implements AutoCloseable {
    enum State { DISABLED, STOPPED, STARTING, RUNNING, UNKNOWN }
    record Snapshot(State state, boolean bootGrace) {}
    private final Ec2Client client;
    private final String instanceId;
    private final Duration grace;
    private final Clock clock;

    @Autowired
    public AiInstanceStatus(
            @Value("${ai.runtime.ec2-instance-id:}") String instanceId,
            @Value("${ai.runtime.ec2-region:ap-northeast-2}") String region,
            @Value("${ai.runtime.boot-grace-seconds:300}") long graceSeconds) {
        this.instanceId=instanceId.trim();
        if (!this.instanceId.isEmpty() && !this.instanceId.matches("i-[0-9a-f]{8}([0-9a-f]{9})?"))
            throw new IllegalArgumentException("AI EC2 인스턴스 ID 형식이 올바르지 않습니다.");
        if (graceSeconds < 0 || graceSeconds > 1800)
            throw new IllegalArgumentException("AI 시작 유예 시간은 0~1800초여야 합니다.");
        this.grace=Duration.ofSeconds(graceSeconds);
        this.clock=Clock.systemUTC();
        // 로컬 기본값에서는 SDK/자격증명/메타데이터 서버에 접근하지 않는다.
        this.client=this.instanceId.isEmpty() ? null : Ec2Client.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(1)).socketTimeout(Duration.ofSeconds(2)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(2))
                        .apiCallAttemptTimeout(Duration.ofSeconds(2)))
                .build();
    }

    AiInstanceStatus(Ec2Client client,String instanceId,Duration grace,Clock clock) {
        this.client=client; this.instanceId=instanceId; this.grace=grace; this.clock=clock;
    }

    Snapshot read() {
        if (client==null) return new Snapshot(State.DISABLED,false);
        try {
            var response=client.describeInstances(DescribeInstancesRequest.builder()
                    .instanceIds(instanceId).build());
            var instances=response.reservations().stream().flatMap(r -> r.instances().stream())
                    .filter(i -> instanceId.equals(i.instanceId())).toList();
            if (instances.size()!=1) return new Snapshot(State.UNKNOWN,false);
            var instance=instances.get(0);
            return switch(instance.state().nameAsString()) {
                case "stopped", "stopping", "terminated", "shutting-down" -> new Snapshot(State.STOPPED,false);
                case "pending" -> new Snapshot(State.STARTING,true);
                case "running" -> new Snapshot(State.RUNNING,inGrace(instance.launchTime()));
                default -> new Snapshot(State.UNKNOWN,false);
            };
        } catch(Exception e) {
            // IAM 거부·timeout·없는 ID를 정지로 추정하지 않는다. AWS 예외/계정 정보도 노출하지 않는다.
            return new Snapshot(State.UNKNOWN,false);
        }
    }

    private boolean inGrace(Instant launched) {
        if (launched==null) return false;
        Duration age=Duration.between(launched,clock.instant());
        return !age.isNegative() && age.compareTo(grace)<0;
    }

    @PreDestroy public void close() { if(client!=null) client.close(); }
}
