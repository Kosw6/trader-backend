package com.example.trader.security.provider;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/** Versioned, salted password hashes. No plaintext or unknown-format fallback. */
public final class Password655 {
    private static final java.util.concurrent.Semaphore SLOTS = new java.util.concurrent.Semaphore(4, true);
    public static final String PREFIX = "{pbkdf2-sha256-600k-v1}";
    private static final Pbkdf2PasswordEncoder ENCODER = new Pbkdf2PasswordEncoder(
            "", 16, 600_000, Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    private static final Argon2PasswordEncoder ARGON = new Argon2PasswordEncoder(16,32,1,19456,2);
    private static final String DUMMY = ARGON.encode("not-a-user-credential");
    private Password655() {}
    private static boolean valid(String raw) {
        return raw != null && !raw.isBlank() && raw.length() <= 4096
                && raw.getBytes(StandardCharsets.UTF_8).length <= 4096;
    }
    public static String encode(String raw) {
        if (!valid(raw)) throw new IllegalArgumentException("Invalid password length");
        return bounded(() -> ARGON.encode(raw));
    }
    public static boolean matches(String raw, String stored) {
        if (!valid(raw)) return false;
        String payload = stored != null && stored.startsWith(PREFIX) ? stored.substring(PREFIX.length()) : "";
        boolean known = payload.matches("[0-9a-f]{96}");
        if (known) return bounded(() -> ENCODER.matches(raw, payload));
        boolean argon = stored != null && stored.matches("\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}");
        boolean matched = bounded(() -> ARGON.matches(raw, argon ? stored : DUMMY));
        return argon && matched;
    }
    private static <T> T bounded(java.util.function.Supplier<T> work) {
        boolean acquired = false;
        try {
            acquired = SLOTS.tryAcquire(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!acquired) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Authentication busy; retry shortly");
            return work.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Authentication interrupted");
        } finally {
            if (acquired) SLOTS.release();
        }
    }
}
