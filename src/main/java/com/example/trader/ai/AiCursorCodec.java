package com.example.trader.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

@Component
public class AiCursorCodec {
    private final String kid;
    private final Map<String, byte[]> keys;
    private final Clock clock;
    private final int ttl;
    @org.springframework.beans.factory.annotation.Autowired
    public AiCursorCodec(@Value("${ai.internal.cursor-kid:}") String kid,
            @Value("${ai.internal.cursor-key:}") String key,
            @Value("${ai.internal.cursor-previous-kid:}") String previousKid,
            @Value("${ai.internal.cursor-previous-key:}") String previousKey,
            @Value("${ai.internal.cursor-ttl-seconds:300}") int ttl) {
        this(kid, parseKeys(kid, key, previousKid, previousKey), ttl, Clock.systemUTC());
    }
    AiCursorCodec(String kid, Map<String, byte[]> keys, int ttl, Clock clock) {
        if (ttl < 1 || ttl > 3600) throw new IllegalArgumentException("커서 유효 시간 설정 오류");
        this.kid = kid; this.keys = Map.copyOf(keys); this.ttl = ttl; this.clock = clock;
    }
    private static Map<String, byte[]> parseKeys(String kid, String key, String oldKid, String oldKey) {
        Map<String, byte[]> result = new HashMap<>();
        addKey(result, kid, key); addKey(result, oldKid, oldKey); return result;
    }
    private static void addKey(Map<String, byte[]> keys, String kid, String value) {
        if (kid.isEmpty() && value.isEmpty()) return;
        try {
            if (!kid.matches("[A-Za-z0-9_-]{1,32}") || !value.matches("[A-Za-z0-9_-]{43,128}")) throw new IllegalArgumentException();
            byte[] bytes = Base64.getUrlDecoder().decode(value);
            if (bytes.length < 32 || keys.putIfAbsent(kid, bytes) != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new IllegalArgumentException("커서 키 설정이 올바르지 않습니다."); }
    }
    private byte[] mac(String keyId, String content) {
        try {
            var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(keys.get(keyId), "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new AiApiException(503, "CURSOR_SIGNER_UNAVAILABLE"); }
    }
    public boolean configured() { return keys.containsKey(kid); }
    public String encode(Map<String, Object> binding, long lastId) {
        return encode(binding,lastId,ttl);
    }
    /** 초기 순회 checkpoint만 24시간 서명. 실행 자체 만료 여부는 별도 DB 검사한다. */
    public String encode(Map<String,Object> binding,long lastId,int lifetimeSeconds) {
        if(lifetimeSeconds<1 || lifetimeSeconds>86400) throw AiApiException.invalid();
        if (!keys.containsKey(kid)) throw new AiApiException(503, "CURSOR_SIGNER_UNAVAILABLE");
        Map<String, Object> payload = new LinkedHashMap<>(binding);
        payload.put("lastId", Long.toString(lastId)); payload.put("expiresAt", clock.instant().getEpochSecond() + lifetimeSeconds);
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String input = kid + "." + encoder.encodeToString(AiCanonicalJson.bytes(payload));
        String token = input + "." + encoder.encodeToString(mac(kid, input));
        if (token.length() > 2048) throw new AiApiException(500, "CURSOR_TOO_LARGE");
        return token;
    }
    public long decode(String token, Map<String, Object> binding) {
        return decode(token,binding,false);
    }
    public long decode(String token,Map<String,Object> binding,boolean allowZero) {
        try {
            if (token.length() > 2048 || !token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) throw AiApiException.invalid();
            var parts = token.split("\\.");
            if (!keys.containsKey(parts[0])) throw AiApiException.invalid();
            if (!MessageDigest.isEqual(mac(parts[0], parts[0] + "." + parts[1]), Base64.getUrlDecoder().decode(parts[2]))) throw AiApiException.invalid();
            Map<String, Object> payload = AiCanonicalJson.MAPPER.readValue(Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {});
            Object expiry = payload.remove("expiresAt"), last = payload.remove("lastId");
            if (!(expiry instanceof Number number) || (expiry instanceof Double) || (expiry instanceof Float)
                    || !payload.equals(binding) || !(last instanceof String)) throw AiApiException.invalid();
            if (number.longValue() <= clock.instant().getEpochSecond()) throw new AiApiException(400, "CURSOR_EXPIRED");
            return allowZero && "0".equals(last) ? 0 : AiScope.id((String) last);
        } catch (AiApiException e) { throw e; }
        catch (Exception e) { throw AiApiException.invalid(); }
    }
}
