package com.example.trader.emailverification;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class EmailVerificationConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "trader.signup-email", name = "enabled", havingValue = "true")
    SesV2Client signupSesClient(EmailVerificationProperties properties) {
        return SesV2Client.builder().region(Region.of(properties.region()))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(2)).socketTimeout(Duration.ofSeconds(5)))
                // Do not automatically repeat a send with an ambiguous delivery outcome.
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(8))
                        .retryPolicy(software.amazon.awssdk.core.retry.RetryPolicy.none()))
                .build(); // Default credentials chain: use the backend's IAM role in production.
    }
}
