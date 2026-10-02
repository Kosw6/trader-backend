package com.example.trader.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** 전용 mapper: 애플리케이션 ObjectMapper 설정 변경과 해시 계약을 격리한다. */
public final class AiCanonicalJson {
    public static final ObjectMapper MAPPER = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private AiCanonicalJson() {}
    public static byte[] bytes(Object value) {
        try { return MAPPER.writeValueAsBytes(value); }
        catch (Exception e) { throw new AiApiException(500, "SERIALIZATION_FAILED"); }
    }
    public static String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(value))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String documentHash(String type, Map<String, Object> document) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("hashPolicyVersion", "v1"); input.put("kind", type + "_DOCUMENT");
        if (type.equals("EDGE")) input.put("label", document.get("label"));
        else {
            for (String field : new String[]{"title", "content", "stockSymbol", type.equals("NOTE") ? "noteDate" : "recordDate"})
                input.put(field, document.get(field));
        }
        return hash(input);
    }
}
