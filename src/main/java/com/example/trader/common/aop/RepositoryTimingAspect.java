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
                    } catch (RuntimeException | Error e) {
                        // RuntimeException / Error 는 래핑 없이 그대로 전파
                        // (DataIntegrityViolationException 등 Spring 예외가 손상되지 않도록)
                        throw e;
                    } catch (Throwable e) {
                        // checked exception 만 RuntimeException 으로 래핑
                        throw new RuntimeException(e);
                    }
                });
    }
}
