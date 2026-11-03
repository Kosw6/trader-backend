package com.example.trader.emailverification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("trader.signup-email")
public record EmailVerificationProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("ap-northeast-2") String region,
        @DefaultValue("") String from,
        @DefaultValue("") String hmacSecret,
        @DefaultValue("") String configurationSet) {
    public EmailVerificationProperties {
        if (enabled && (from == null || from.isBlank() || region == null || region.isBlank()
                || hmacSecret == null || hmacSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32))
            throw new IllegalArgumentException("Signup email requires SES region/from and a secret of at least 32 bytes");
    }
    @Override public String toString() {
        return "EmailVerificationProperties[enabled=" + enabled + ", region=" + region + ", secrets=REDACTED]";
    }
}
