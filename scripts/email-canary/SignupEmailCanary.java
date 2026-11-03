package com.example.trader.canary;

import com.example.trader.emailverification.*;
import com.example.trader.policy.*;
import com.example.trader.service.UserService;
import com.example.trader.repository.UserRepository;
import com.example.trader.controller.loginController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Loopback-only verification fixture, never packaged into the production JAR.
 * No component scan, scheduling, consumer or relay registrations.
 * Only the user's explicit signup submission writes the ordinary account/consent transaction.
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@EntityScan("com.example.trader")
@EnableJpaRepositories(basePackageClasses = UserRepository.class)
@EnableJpaAuditing
@Import({ EmailVerificationConfig.class, EmailVerificationController.class,
        EmailVerificationExceptionHandler.class, RedisEmailVerificationStore.class,
        EmailVerificationService.class, SesVerificationMailSender.class,
        UserService.class, PolicyConsentService.class, PolicyCatalog.class,
        PolicyController.class, OAuthConsentIntent.class, PolicyExceptionHandler.class,
        com.example.trader.common.GlobalExceptionHandler.class })
public class SignupEmailCanary {
    public static void main(String[] args) { SpringApplication.run(SignupEmailCanary.class, args); }
    @Bean loginController signup(UserService users, PolicyConsentService consents, EmailVerificationService email) {
        return new loginController(null, null, users, consents, email);
    }
    @Bean SecurityFilterChain canarySecurity(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a
                .requestMatchers("/api/login/**", "/actuator/health", "/error").permitAll()
                .anyRequest().denyAll()).build();
    }
}
