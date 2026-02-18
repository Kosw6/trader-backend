package com.example.trader.common.aop;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class RepositoryTimingAspect {

    private final MeterRegistry registry;

    @Around("execution(* com.example.trader.repository..*(..))")
    public Object measure(ProceedingJoinPoint pjp) throws Throwable {
        return Timer.builder("db.query")
                .tag("method", pjp.getSignature().getName())
                .publishPercentileHistogram()
                .register(registry)
                .recordCallable(() -> {
                    try {
                        return pjp.proceed();
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
