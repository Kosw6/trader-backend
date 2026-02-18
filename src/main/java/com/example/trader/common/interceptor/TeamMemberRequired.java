package com.example.trader.common.interceptor;
import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TeamMemberRequired {
    String teamIdVar() default "teamId";
}
