package com.example.trader.ai;

import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiInstanceStatusTest {
    private final Instant now=Instant.parse("2026-10-08T00:00:00Z");
    private final String id="i-0123456789abcdef0";
    private final Ec2Client client=mock(Ec2Client.class);
    private final AiInstanceStatus service=new AiInstanceStatus(client,id,Duration.ofSeconds(300),Clock.fixed(now,ZoneOffset.UTC));

    private void response(String state,long age) {
        when(client.describeInstances(any(DescribeInstancesRequest.class))).thenReturn(
                DescribeInstancesResponse.builder().reservations(Reservation.builder().instances(
                        Instance.builder().instanceId(id).state(InstanceState.builder().name(state).build())
                                .launchTime(now.minusSeconds(age)).build()).build()).build());
    }

    @Test void stoppedAndPendingDoNotProbeTheApplication() {
        for(String state : new String[]{"stopped","stopping","terminated","shutting-down","pending"}) {
            response(state,0);
            var result=AiAvailabilityController.resolve(service.read(), () -> { fail("정지/기동 중 서버를 호출하면 안 됩니다."); return Map.of(); });
            assertEquals(state.equals("pending") ? "STARTING" : "STOPPED",result.get("state"));
        }
        verify(client, times(5)).describeInstances(argThat((DescribeInstancesRequest r) -> r.instanceIds().equals(java.util.List.of(id))));
    }

    @Test void runningStillRequiresReadinessAndStartupGraceExpires() {
        response("running",20);
        assertEquals("STARTING", resolve("UNAVAILABLE"));
        assertEquals("READY", resolve("READY"));
        assertEquals("WARMING", resolve("WARMING"));
        response("running",300);
        assertEquals("UNAVAILABLE", resolve("UNAVAILABLE"));
        assertEquals("READY", resolve("READY"));
    }

    @Test void awsFailuresAndMissingTargetsAreNotOff() {
        when(client.describeInstances(any(DescribeInstancesRequest.class))).thenThrow(new RuntimeException("private"));
        assertEquals(AiInstanceStatus.State.UNKNOWN, service.read().state());
        assertEquals("UNAVAILABLE",resolve("READY"));
        reset(client);
        when(client.describeInstances(any(DescribeInstancesRequest.class))).thenReturn(DescribeInstancesResponse.builder().build());
        assertEquals(AiInstanceStatus.State.UNKNOWN, service.read().state());
    }

    @Test void localModeDoesNotConstructAwsClientAndRetainsHttpProbe() {
        try(var local=new AiInstanceStatus("", "ap-northeast-2",300)) {
            assertEquals(AiInstanceStatus.State.DISABLED,local.read().state());
            assertEquals("READY",AiAvailabilityController.resolve(local.read(),()->Map.of("state","READY")).get("state"));
        }
        assertThrows(IllegalArgumentException.class,()->new AiInstanceStatus("not-an-id","ap-northeast-2",300));
    }

    private Object resolve(String runtime) {
        return AiAvailabilityController.resolve(service.read(),()->Map.of("state",runtime)).get("state");
    }
}
