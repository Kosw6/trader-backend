package com.example.trader.realtime.outbox;

import org.apache.kafka.common.errors.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** 단계와 복구 방향을 함께 보존한다. 알 수 없는 오류만으로 Kafka DOWN을 선언하지 않는다. */
enum OutboxFailure {
    INVALID_EVENT(true, false),
    PUBLISH_PERMANENT(true, false),
    PUBLISH_UNAVAILABLE(false, true),
    PUBLISH_RETRYABLE(false, false),
    PUBLISH_UNKNOWN(false, false),
    INTERRUPTED(false, false),
    SENT_RECORD_FAILED(false, false);

    final boolean quarantine;
    final boolean unavailable;

    OutboxFailure(boolean quarantine, boolean unavailable) {
        this.quarantine = quarantine;
        this.unavailable = unavailable;
    }

    static OutboxFailure classifyPublish(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        OutboxFailure result = PUBLISH_UNKNOWN;
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof InterruptException) {
                return INTERRUPTED;
            }
            if (cause instanceof SerializationException || cause instanceof AuthenticationException
                    || cause instanceof AuthorizationException || cause instanceof RecordTooLargeException
                    || cause instanceof InvalidTopicException || cause instanceof IllegalArgumentException) {
                result = PUBLISH_PERMANENT;
            } else if (result != PUBLISH_PERMANENT) {
                if (cause instanceof java.util.concurrent.TimeoutException
                        || cause instanceof TimeoutException || cause instanceof NetworkException) {
                    result = PUBLISH_UNAVAILABLE;
                } else if (cause instanceof RetriableException && result != PUBLISH_UNAVAILABLE) {
                    result = PUBLISH_RETRYABLE;
                }
            }
        }
        return result;
    }
}
