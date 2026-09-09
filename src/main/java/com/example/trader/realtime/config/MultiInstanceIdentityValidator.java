package com.example.trader.realtime.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 다중 서버 전파에서 자기 발행 이벤트를 식별하기 위한 인스턴스 ID 불변식을 검증한다.
 */
@Component
@ConditionalOnProperty(name = "app.mode", havingValue = "multi")
public class MultiInstanceIdentityValidator {

    public MultiInstanceIdentityValidator(@Value("${app.instance-id:}") String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            throw new IllegalStateException(
                    "multi mode requires a non-blank app.instance-id supplied by the runtime environment"
            );
        }

        if ("local".equalsIgnoreCase(instanceId.trim())) {
            throw new IllegalStateException(
                    "multi mode cannot use the shared default app.instance-id 'local'"
            );
        }
    }
}
