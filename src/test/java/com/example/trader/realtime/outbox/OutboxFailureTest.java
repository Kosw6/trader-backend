package com.example.trader.realtime.outbox;

import org.apache.kafka.common.errors.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

class OutboxFailureTest {
    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new SerializationException("직렬화"), OutboxFailure.PUBLISH_PERMANENT),
                Arguments.of(new AuthenticationException("인증"), OutboxFailure.PUBLISH_PERMANENT),
                Arguments.of(new AuthorizationException("권한"), OutboxFailure.PUBLISH_PERMANENT),
                Arguments.of(new RecordTooLargeException("크기"), OutboxFailure.PUBLISH_PERMANENT),
                Arguments.of(new InvalidTopicException("토픽"), OutboxFailure.PUBLISH_PERMANENT),
                Arguments.of(new NetworkException("네트워크"), OutboxFailure.PUBLISH_UNAVAILABLE),
                Arguments.of(new TimeoutException("응답 시간 초과"), OutboxFailure.PUBLISH_UNAVAILABLE),
                Arguments.of(new NotEnoughReplicasException("복제 수 부족"), OutboxFailure.PUBLISH_RETRYABLE),
                Arguments.of(new IllegalStateException("알 수 없음"), OutboxFailure.PUBLISH_UNKNOWN),
                Arguments.of(new InterruptedException("중단"), OutboxFailure.INTERRUPTED));
    }

    @ParameterizedTest
    @MethodSource("failures")
    void unwraps_nested_send_failures(Throwable cause, OutboxFailure expected) {
        assertThat(OutboxFailure.classifyPublish(new ExecutionException(
                new org.springframework.kafka.KafkaException("발행 실패", cause))))
                .isEqualTo(expected);
    }
}
