package com.example.trader.realtime.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MultiInstanceIdentityValidatorTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MultiInstanceIdentityValidator.class);

    @Test
    void multiModeRequiresInstanceId() {
        contextRunner
                .withPropertyValues("app.mode=multi", "app.instance-id=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "multi mode requires a non-blank app.instance-id supplied by the runtime environment"
                            );
                });
    }

    @Test
    void multiModeRejectsSharedLocalId() {
        contextRunner
                .withPropertyValues("app.mode=multi", "app.instance-id=local")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "multi mode cannot use the shared default app.instance-id 'local'"
                            );
                });
    }

    @Test
    void multiModeAcceptsExplicitInstanceId() {
        contextRunner
                .withPropertyValues("app.mode=multi", "app.instance-id=trader-backend-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MultiInstanceIdentityValidator.class);
                });
    }

    @Test
    void singleModeDoesNotRequireInstanceId() {
        contextRunner
                .withPropertyValues("app.mode=single", "app.instance-id=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MultiInstanceIdentityValidator.class);
                });
    }
}
