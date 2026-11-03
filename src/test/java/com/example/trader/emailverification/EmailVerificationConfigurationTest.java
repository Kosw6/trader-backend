package com.example.trader.emailverification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationConfigurationTest {
    final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(EmailVerificationConfig.class);
    @Test void disabledByDefaultDoesNotCreateAwsClient() {
        runner.run(context->{assertThat(context).hasNotFailed().doesNotHaveBean(SesV2Client.class);
            assertThat(context.getBean(EmailVerificationProperties.class).enabled()).isFalse();});
    }
    @Test void cannotEnableWithoutSenderAndSecret() {
        runner.withPropertyValues("trader.signup-email.enabled=true").run(context->assertThat(context).hasFailed());
    }
    @Test void bindsEnabledConfigAndDoesNotExposeSecretInToString() {
        runner.withPropertyValues("trader.signup-email.enabled=true","trader.signup-email.from=noreply@example.invalid",
                "trader.signup-email.hmac-secret="+"s".repeat(40)).run(context->{
            assertThat(context).hasNotFailed().hasSingleBean(SesV2Client.class);
            assertThat(context.getBean(EmailVerificationProperties.class).toString()).doesNotContain("s".repeat(40));
        });
    }
}
