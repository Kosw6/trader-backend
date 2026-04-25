package com.example.trader.observability;

import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ObservedLogAspect {//observedLog 어노테이션 붙인 메서드 로깅 aop

    @Around("@annotation(observedLog)")
    public Object observe(ProceedingJoinPoint joinPoint, ObservedLog observedLog) throws Throwable {
        //실행 시간 측정용
        long start = System.currentTimeMillis();

        MDC.put("domain", observedLog.domain());
        MDC.put("api", observedLog.api());

        try {
            //본 메서드 실행
            Object result = joinPoint.proceed();
            //걸린 시간
            long elapsedMs = System.currentTimeMillis() - start;

            MDC.put("event", "API_SUCCESS");
            MDC.put("elapsedMs", String.valueOf(elapsedMs));

            log.info("api request success");

            return result;

        } catch (BaseException ex) {//사용자 지정 에러인 경우
            long elapsedMs = System.currentTimeMillis() - start;
            BaseResponseStatus status = ex.getStatus();

            MDC.put("event", "API_ERROR");
            MDC.put("domain", status.getDomain());
            MDC.put("errorCode", status.getErrorCode());
            MDC.put("severity", status.getSeverity().name());
            MDC.put("elapsedMs", String.valueOf(elapsedMs));
            MDC.put("exception", ex.getClass().getSimpleName());
            MDC.put("httpStatus", String.valueOf(status.getCode()));

            log.error("base exception occurred", ex);

            throw ex;

        } catch (Exception ex) {//사용자 지정 에러가 아닌 경우
            long elapsedMs = System.currentTimeMillis() - start;

            MDC.put("event", "API_ERROR");
            MDC.put("errorCode", BaseResponseStatus.INTERNAL_SERVER_ERROR.getErrorCode());
            MDC.put("severity", BaseResponseStatus.INTERNAL_SERVER_ERROR.getSeverity().name());
            MDC.put("elapsedMs", String.valueOf(elapsedMs));
            MDC.put("exception", ex.getClass().getSimpleName());
            MDC.put("httpStatus", String.valueOf(BaseResponseStatus.INTERNAL_SERVER_ERROR.getCode()));

            log.error("unexpected exception occurred", ex);

            throw ex;

        } finally {//MDC비워서 다른 쓰레드 작업에 영향이 없게끔
            clearObservationMdc();
        }
    }

    private void clearObservationMdc() {
        MDC.remove("event");
        MDC.remove("domain");
        MDC.remove("api");
        MDC.remove("errorCode");
        MDC.remove("severity");
        MDC.remove("elapsedMs");
        MDC.remove("exception");
        MDC.remove("httpStatus");
    }
}