package com.example.trader.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;

public record AiEvidenceCheck(String evidenceId, String usage, String type, long sourceId,
                              AiScope scope, Map<String,Object> fields) {
    public String text(String field) { return (String)fields.get(field); }
    public static List<AiEvidenceCheck> parse(String json, AiRequestContext context) {
        try {
            if (json == null || context.indexing()) throw AiApiException.invalid();
            List<Map<String,Object>> entries = AiCanonicalJson.MAPPER.readValue(json,new TypeReference<>() {});
            if (entries.isEmpty() || entries.size()>20) throw AiApiException.invalid();
            Set<String> ids=new HashSet<>(); List<AiEvidenceCheck> results=new ArrayList<>();
            for (var entry:entries) {
                String id=string(entry,"evidenceId"), usage=string(entry,"usage"), type=string(entry,"sourceType");
                if(!id.matches("[A-Za-z0-9_-]{1,64}") || !ids.add(id) || !Set.of("NOTE","NODE","EDGE").contains(type)) throw AiApiException.invalid();
                long sourceId=AiScope.id(string(entry,"sourceId")); AiScope.id(string(entry,"observedRevision"));
                if(!(entry.get("scope") instanceof Map<?,?> scope)) throw AiApiException.invalid();
                String scopeType=string(scope,"type");
                if(!scope.keySet().equals(scopeType.equals("TEAM")?Set.of("type","teamId"):Set.of("type"))) throw AiApiException.invalid();
                AiScope resolved=AiScope.of(context,scopeType,scope.containsKey("teamId")?string(scope,"teamId"):null);
                Set<String> allowed=new HashSet<>(Set.of("evidenceId","usage","sourceType","sourceId","observedRevision","scope"));
                if(usage.equals("DOCUMENT")) {
                    allowed.addAll(Set.of("hashPolicyVersion","documentHash")); hash(entry,"documentHash");
                } else {
                    if(!type.equals("NODE")) throw AiApiException.invalid();
                    String relation=string(entry,"relationType");
                    if(!Set.of("LINKED_NOTES","INCIDENT_EDGES").contains(relation)) throw AiApiException.invalid();
                    allowed.add("relationType");
                    if(usage.equals("RELATION_LIST")) {allowed.addAll(Set.of("hashPolicyVersion","relationsHash"));hash(entry,"relationsHash");}
                    else if(usage.equals("RELATION")) {
                        var required=relation.equals("LINKED_NOTES")?Set.of("targetSourceId"):Set.of("edgeId","sourceNodeId","targetNodeId");
                        allowed.addAll(required); for(String key:required) AiScope.id(string(entry,key));
                        if(relation.equals("INCIDENT_EDGES") && sourceId!=AiScope.id(string(entry,"sourceNodeId")) && sourceId!=AiScope.id(string(entry,"targetNodeId"))) throw AiApiException.invalid();
                    } else throw AiApiException.invalid();
                }
                if(!entry.keySet().equals(allowed)) throw AiApiException.invalid();
                results.add(new AiEvidenceCheck(id,usage,type,sourceId,resolved,entry));
            }
            return results;
        } catch(AiApiException e) {throw e;} catch(Exception e) {throw AiApiException.invalid();}
    }
    private static String string(Map<?,?> value,String field) {
        if(!(value.get(field) instanceof String s) || s.isEmpty()) throw AiApiException.invalid(); return s;
    }
    private static void hash(Map<String,Object> value,String field) {
        if(!string(value,field).matches("[0-9a-f]{64}") || string(value,"hashPolicyVersion").length()>32) throw AiApiException.invalid();
    }
}
