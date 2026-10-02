package com.example.trader.security.provider;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 외부 주입 키만 사용. 재시작 임의 생성/HMAC fallback 없음. */
@Component
public class JwtKeyRing {
    private final String activeKid;
    private final RSAPrivateKey privateKey;
    private final Map<String, RSAPublicKey> publicKeys;

    public JwtKeyRing(@Value("${auth.jwt.active-kid:}") String activeKid,
            @Value("${auth.jwt.private-key-path:}") String privatePath,
            @Value("${auth.jwt.public-key-directory:}") String publicDirectory) {
        try {
            if (!activeKid.matches("[A-Za-z0-9_-]{1,64}") || privatePath.isBlank() || publicDirectory.isBlank()) throw new IllegalArgumentException();
            var factory = KeyFactory.getInstance("RSA");
            var privateFile = Path.of(privatePath);
            if (Files.size(privateFile) > 32768) throw new IllegalArgumentException();
            privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(decodePem(Files.readString(privateFile), "PRIVATE KEY")));
            Map<String, RSAPublicKey> loaded = new TreeMap<>();
            try (var paths = Files.list(Path.of(publicDirectory))) {
                var files = paths.filter(p -> p.getFileName().toString().endsWith(".pem")).toList();
                if (files.isEmpty() || files.size() > 16) throw new IllegalArgumentException();
                for (var path : files) {
                    String name = path.getFileName().toString();
                    String kid = name.substring(0, name.length() - 4);
                    if (!kid.matches("[A-Za-z0-9_-]{1,64}") || Files.size(path) > 32768) throw new IllegalArgumentException();
                    var key = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(decodePem(Files.readString(path), "PUBLIC KEY")));
                    if (key.getModulus().bitLength() < 2048 || key.getModulus().bitLength() > 8192) throw new IllegalArgumentException();
                    loaded.put(kid, key);
                }
            }
            var active = loaded.get(activeKid);
            if (!(privateKey instanceof RSAPrivateCrtKey crt) || active == null
                    || !active.getModulus().equals(crt.getModulus())
                    || !active.getPublicExponent().equals(crt.getPublicExponent())) throw new IllegalArgumentException();
            this.activeKid = activeKid;
            this.publicKeys = Map.copyOf(loaded);
        } catch (Exception ignored) {
            throw new IllegalArgumentException("JWT 키 설정이 없거나 유효하지 않습니다. PKCS8 개인키와 일치하는 공개키를 확인하세요.");
        }
    }
    private static byte[] decodePem(String pem, String type) {
        String begin = "-----BEGIN " + type + "-----", end = "-----END " + type + "-----";
        String text = pem.strip();
        if (!text.startsWith(begin) || !text.endsWith(end)) throw new IllegalArgumentException();
        return Base64.getDecoder().decode(text.substring(begin.length(), text.length() - end.length()).replaceAll("\\s", ""));
    }
    public String activeKid() { return activeKid; }
    public RSAPrivateKey privateKey() { return privateKey; }
    public RSAPublicKey publicKey(String kid) {
        if (kid == null || !publicKeys.containsKey(kid)) throw new IllegalArgumentException("알 수 없는 서명 키입니다.");
        return publicKeys.get(kid);
    }
    public Map<String, Object> jwks() {
        var keys = publicKeys.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> Map.of("kty", "RSA", "use", "sig", "alg", "RS256", "kid", entry.getKey(),
                        "n", unsigned(entry.getValue().getModulus()), "e", unsigned(entry.getValue().getPublicExponent()))).toList();
        return Map.of("keys", keys);
    }
    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
