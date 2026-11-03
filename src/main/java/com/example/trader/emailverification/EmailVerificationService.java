package com.example.trader.emailverification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]{1,64}@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}");
    private final EmailVerificationProperties properties;
    private final EmailVerificationStore store;
    private final VerificationMailSender sender;

    public EmailVerificationService(EmailVerificationProperties properties, EmailVerificationStore store, VerificationMailSender sender) {
        this.properties = properties;
        this.store = store;
        this.sender = sender;
    }

    public boolean enabled() { return properties.enabled(); }
    public record Requested(String challengeId, int expiresInSeconds, int resendAfterSeconds) {}
    public record Verified(String emailVerificationToken, int expiresInSeconds) {}

    public Requested request(String input, String clientAddress) {
        requireEnabled();
        String email = normalize(input), emailHash = mac("email", email);
        String challenge = randomToken();
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        boolean issued;
        try { issued = store.issue(emailHash, mac("client", clientAddress == null ? "unknown" : clientAddress),
                challenge, mac("otp", email + "\n" + challenge + "\n" + code)); }
        catch (RuntimeException ex) { throw unavailable(); }
        if (!issued) throw new EmailVerificationException(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_VERIFICATION_RATE_LIMITED");
        try { sender.send(email, code); }
        catch (RuntimeException ex) {
            try { store.cancel(emailHash, challenge); } catch (RuntimeException ignored) { /* Keep fail-closed. */ }
            throw new EmailVerificationException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_UNAVAILABLE");
        }
        return new Requested(challenge, 600, 60);
    }

    public Verified confirm(String input, String challengeId, String code) {
        requireEnabled();
        String email = normalize(input);
        if (challengeId == null || !challengeId.matches("[a-f0-9]{64}") || code == null || !code.matches("[0-9]{6}"))
            throw invalid();
        String token = randomToken();
        boolean verified;
        try { verified = store.confirm(mac("email", email), challengeId,
                mac("otp", email + "\n" + challengeId + "\n" + code), digest(token)); }
        catch (RuntimeException ex) { throw unavailable(); }
        if (!verified) throw invalid();
        return new Verified(token, 600);
    }

    /** Invoked only for local signup, before any account/consent write. No cache fallback. */
    public String consumeForSignup(String input, String token) {
        if (!enabled()) return input; // Explicit rollout switch; SES/Redis aren't touched.
        String email = normalize(input);
        if (token == null || !token.matches("[a-f0-9]{64}")) throw required();
        boolean consumed;
        try { consumed = store.consume(mac("email", email), digest(token)); }
        catch (RuntimeException ex) { throw unavailable(); }
        if (!consumed) throw required();
        return email;
    }

    static String normalize(String input) {
        if (input == null) throw badEmail();
        String email = input.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) throw badEmail();
        String local = email.substring(0, email.indexOf('@'));
        if (local.startsWith(".") || local.endsWith(".") || local.contains("..")) throw badEmail();
        return email;
    }
    private static String randomToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
    private String mac(String purpose, String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.hmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((purpose + "\n" + input).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) { throw unavailable(); }
    }
    private static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.GeneralSecurityException ex) { throw unavailable(); }
    }
    private void requireEnabled() { if (!enabled()) throw unavailable(); }
    private static EmailVerificationException unavailable() { return new EmailVerificationException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_VERIFICATION_UNAVAILABLE"); }
    private static EmailVerificationException required() { return new EmailVerificationException(HttpStatus.BAD_REQUEST, "EMAIL_VERIFICATION_REQUIRED"); }
    private static EmailVerificationException invalid() { return new EmailVerificationException(HttpStatus.BAD_REQUEST, "EMAIL_VERIFICATION_INVALID_OR_EXPIRED"); }
    private static EmailVerificationException badEmail() { return new EmailVerificationException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL"); }
}
