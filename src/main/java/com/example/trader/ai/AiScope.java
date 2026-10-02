package com.example.trader.ai;

import java.util.LinkedHashMap;
import java.util.Map;

public record AiScope(boolean indexing, Long userId, String type, Long teamId) {
    public static long id(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw AiApiException.invalid();
            return Long.parseLong(value);
        } catch (NumberFormatException e) { throw AiApiException.invalid(); }
    }
    public static AiScope of(AiRequestContext context, String type, String teamId) {
        if (context.indexing()) {
            if (type != null || teamId != null) throw AiApiException.invalid();
            return new AiScope(true, null, null, null);
        }
        if ("PERSONAL".equals(type) && teamId == null) return new AiScope(false, context.userId(), type, null);
        if ("TEAM".equals(type)) return new AiScope(false, context.userId(), type, id(teamId));
        throw AiApiException.invalid();
    }
    public Map<String, Object> canonical() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("purpose", indexing ? "INDEXING" : "RETRIEVAL");
        if (indexing) out.put("indexingScopeId", "all-sources-v1");
        else { out.put("userId", userId.toString()); out.put("scopeType", type); out.put("teamId", teamId == null ? null : teamId.toString()); }
        return out;
    }
}
