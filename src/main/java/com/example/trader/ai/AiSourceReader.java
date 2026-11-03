package com.example.trader.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 원문·권한·검색 revision을 한 PostgreSQL MVCC snapshot에서 읽는다. */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 3)
public class AiSourceReader {
    private final JdbcTemplate db;
    private final AiReadLimits limits;
    @org.springframework.beans.factory.annotation.Autowired
    public AiSourceReader(JdbcTemplate db, AiReadLimits limits) { this.db = db; this.limits=limits; }
    AiSourceReader(JdbcTemplate db) { this(db,new AiReadLimits(1048576,30,100,10000)); }
    private static String table(String type) {
        return switch (type) { case "NOTE" -> "note"; case "NODE" -> "node"; case "EDGE" -> "edge"; default -> throw AiApiException.invalid(); };
    }
    private boolean exists(String sql, Object... args) { return Boolean.TRUE.equals(db.queryForObject(sql, Boolean.class, args)); }
    /** 현재 권한 안의 정확 제목 후보만 조회. 2개는 전체 목록이 아니라 동명 모호성 표식이다. */
    public Map<String, Object> exactTitle(String title, AiScope scope) {
        if (scope.indexing() || title == null || title.isBlank() || title.length() > 255)
            throw AiApiException.invalid();
        if (scope.teamId() != null && !exists(
                "select exists(select 1 from user_team where user_id=? and team_id=?)",
                scope.userId(), scope.teamId())) throw new AiApiException(404, "SOURCE_UNAVAILABLE");
        // 정규식/SQL은 서버 고정. 제목은 바인딩하며 와일드카드로 해석하지 않는다.
        String match = "lower(regexp_replace(s.subject, '[[:space:]]', '', 'g'))="
                + "lower(regexp_replace(?, '[[:space:]]', '', 'g'))";
        String notes = "select 'NOTE' as type,s.id from note s where " + match + " and ";
        String nodes = "select 'NODE' as type,s.id from node s join page p on p.id=s.page_id "
                + "join directory d on d.id=p.directory_id where " + match + " and ";
        List<Map<String,Object>> rows;
        if (scope.teamId() == null) {
            rows = db.queryForList("(" + notes + "s.user_id=? limit 2) union all (" + nodes
                    + "d.team_id is null and d.user_id=? and p.user_id=? limit 2) limit 2",
                    title, scope.userId(), title, scope.userId(), scope.userId());
        } else {
            rows = db.queryForList("(" + notes + "exists(select 1 from node_note_link l "
                    + "join node n on n.id=l.node_id join page p on p.id=n.page_id "
                    + "join directory d on d.id=p.directory_id where l.note_id=s.id and d.team_id=?) "
                    + "limit 2) union all (" + nodes + "d.team_id=? limit 2) limit 2",
                    title, scope.teamId(), title, scope.teamId());
        }
        return Map.of("status", rows.size() > 1 ? "AMBIGUOUS" : "RESOLVED", "items",
                rows.stream().map(r -> Map.of("sourceType", r.get("type"),
                        "sourceId", r.get("id").toString())).toList());
    }
    public boolean allowed(String type, long id, AiScope scope) {
        table(type);
        if (scope.indexing()) return true;
        if (scope.teamId() != null && !exists("select exists(select 1 from user_team where user_id=? and team_id=?)", scope.userId(), scope.teamId())) return false;
        if (type.equals("NOTE")) {
            if (scope.teamId() == null) return exists("select exists(select 1 from note where id=? and user_id=?)", id, scope.userId());
            return exists("select exists(select 1 from node_note_link l join node n on n.id=l.node_id join page p on p.id=n.page_id join directory d on d.id=p.directory_id where l.note_id=? and d.team_id=?)", id, scope.teamId());
        }
        String sql = "select exists(select 1 from " + table(type) + " s join page p on p.id=s.page_id join directory d on d.id=p.directory_id where s.id=? and ";
        return scope.teamId() == null
                ? exists(sql + "d.team_id is null and d.user_id=? and p.user_id=?)", id, scope.userId(), scope.userId())
                : exists(sql + "d.team_id=?)", id, scope.teamId());
    }
    public Map<String, Object> source(String type, long id, Long expected, AiScope scope) {
        table(type);
        // 사용자에게 존재·삭제·revision을 노출하기 전에 현재 자료 경로 확인.
        if (!allowed(type, id, scope)) throw new AiApiException(404, "SOURCE_UNAVAILABLE");
        var state = db.queryForList("select revision, deleted from search_source_state where source_type=? and source_id=?", type, id);
        String length = type.equals("EDGE") ? "coalesce(octet_length(label),0)" :
                "coalesce(octet_length(subject),0)+coalesce(octet_length(content),0)+coalesce(octet_length("+(type.equals("NOTE")?"stock_symb":"symb")+"),0)";
        var sizes=db.queryForList("select " + length + " as bytes from " + table(type) + " where id=?",id);
        if (state.isEmpty()) throw new AiApiException(sizes.isEmpty() ? 404 : 409, sizes.isEmpty() ? "SOURCE_NOT_FOUND" : "SOURCE_STATE_INCONSISTENT");
        long revision = ((Number) state.get(0).get("revision")).longValue();
        boolean deleted = (Boolean) state.get(0).get("deleted");
        if (deleted != sizes.isEmpty()) throw new AiApiException(409, "SOURCE_STATE_INCONSISTENT");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sourceType", type); result.put("sourceId", Long.toString(id));
        result.put("sourceRevision", Long.toString(revision));
        if (deleted) { result.put("status", "DELETED"); return result; }
        if (expected != null && expected != revision) {
            result.put("status", "REVISION_MISMATCH"); result.put("expectedRevision", expected.toString());
            result.put("currentRevision", Long.toString(revision)); return result;
        }
        if(((Number)sizes.get(0).get("bytes")).longValue()>limits.sourceBytes())
            throw new AiApiException(413,"SOURCE_RESPONSE_TOO_LARGE");
        var row = db.queryForMap("select * from " + table(type) + " where id=?", id);
        Map<String, Object> doc = new LinkedHashMap<>();
        if (type.equals("EDGE")) {
            long page = ((Number) row.get("page_id")).longValue();
            if (!exists("select exists(select 1 from node s join node t on t.id=? where s.id=? and s.page_id=? and t.page_id=?)",
                    row.get("target_id"), row.get("source_id"), page, page)) throw new AiApiException(409, "SOURCE_STATE_INCONSISTENT");
            doc.put("sourceNodeId", row.get("source_id").toString()); doc.put("targetNodeId", row.get("target_id").toString());
            doc.put("canvasId", row.get("page_id").toString()); doc.put("label", row.get("label"));
        } else {
            Object title = row.get("subject"), content = row.get("content"), date = row.get(type.equals("NOTE") ? "note_date" : "record_date");
            if (!(title instanceof String text) || text.isBlank() || !(content instanceof String) || date == null)
                throw new AiApiException(409, "SOURCE_STATE_INCONSISTENT");
            doc.put("title", title); doc.put("content", content);
            doc.put("stockSymbol", row.get(type.equals("NOTE") ? "stock_symb" : "symb"));
            doc.put(type.equals("NOTE") ? "noteDate" : "recordDate", date.toString());
            if (type.equals("NODE")) doc.put("canvasId", row.get("page_id").toString());
        }
        result.put("status", "AVAILABLE"); result.put("hashPolicyVersion", "v1");
        result.put("documentHash", AiCanonicalJson.documentHash(type, doc)); result.put("document", doc);
        if (scope.indexing()) result.put("scopeKeys", scopeKeys(type, id, row));
        return result;
    }
    private List<String> scopeKeys(String type, long id, Map<String, Object> row) {
        var result = new TreeSet<String>();
        if (type.equals("NOTE")) {
            result.add("PERSONAL:" + row.get("user_id"));
            db.queryForList("select distinct d.team_id from node_note_link l join node n on n.id=l.node_id join page p on p.id=n.page_id join directory d on d.id=p.directory_id where l.note_id=? and d.team_id is not null", id)
                    .forEach(r -> result.add("TEAM:" + r.get("team_id")));
        } else {
            var page = db.queryForMap("select p.user_id, d.team_id from page p join directory d on d.id=p.directory_id where p.id=?", row.get("page_id"));
            result.add(page.get("team_id") == null ? "PERSONAL:" + page.get("user_id") : "TEAM:" + page.get("team_id"));
        }
        return List.copyOf(result);
    }

    public Map<String, Object> relations(long nodeId, long expected, String kind, AiScope scope,
                                         int size, String cursor, AiCursorCodec codec) {
        if (size < 1 || size > limits.maxPage() || kind == null || !Set.of("LINKED_NOTES", "INCIDENT_EDGES").contains(kind)) throw AiApiException.invalid();
        var source = source("NODE", nodeId, expected, scope);
        if (!"AVAILABLE".equals(source.get("status"))) return source;
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("version", 1); binding.put("sourceId", Long.toString(nodeId));
        binding.put("revision", Long.toString(expected)); binding.put("relationType", kind);
        binding.put("scope", scope.canonical()); binding.put("pageSize", size);
        long after = cursor == null ? 0 : codec.decode(cursor, binding);
        List<Map<String, Object>> items = relationItems(nodeId, kind, scope);
        var selected = items.stream().filter(i -> Long.parseLong(i.get(kind.equals("LINKED_NOTES") ? "noteId" : "edgeId").toString()) > after).limit(size + 1L).toList();
        boolean more = selected.size() > size;
        var page = more ? selected.subList(0, size) : selected;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "AVAILABLE"); result.put("sourceType", "NODE"); result.put("sourceId", Long.toString(nodeId));
        result.put("sourceRevision", Long.toString(expected)); result.put("relationType", kind);
        result.put("hashPolicyVersion", "v1"); result.put("relationsHash", relationHash(nodeId, kind, scope, items));
        result.put("items", page); result.put("completed", !more);
        result.put("nextCursor", more ? codec.encode(binding, Long.parseLong(page.get(page.size()-1).get(kind.equals("LINKED_NOTES") ? "noteId" : "edgeId").toString())) : null);
        return result;
    }
    private List<Map<String, Object>> relationItems(long nodeId, String kind, AiScope scope) {
        List<Map<String, Object>> rows;
        if (kind.equals("LINKED_NOTES")) {
            rows = scope.indexing() || scope.teamId() != null
                    ? db.queryForList("select distinct l.note_id from node_note_link l join note n on n.id=l.note_id where l.node_id=? order by l.note_id limit ?", nodeId,limits.relationSetItems()+1)
                    : db.queryForList("select distinct l.note_id from node_note_link l join note n on n.id=l.note_id where l.node_id=? and n.user_id=? order by l.note_id limit ?", nodeId, scope.userId(),limits.relationSetItems()+1);
        } else {
            rows = db.queryForList("select e.id,e.source_id,e.target_id from edge e join node s on s.id=e.source_id join node t on t.id=e.target_id where (e.source_id=? or e.target_id=?) and e.page_id=s.page_id and e.page_id=t.page_id order by e.id limit ?", nodeId, nodeId,limits.relationSetItems()+1);
        }
        if (rows.size() > limits.relationSetItems()) throw new AiApiException(413, "RELATION_SET_TOO_LARGE");
        return rows.stream().map(row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            if (kind.equals("LINKED_NOTES")) item.put("noteId", row.get("note_id").toString());
            else { item.put("edgeId", row.get("id").toString()); item.put("sourceNodeId", row.get("source_id").toString()); item.put("targetNodeId", row.get("target_id").toString()); }
            return item;
        }).toList();
    }
    private String relationHash(long nodeId, String kind, AiScope scope, List<Map<String, Object>> items) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("hashPolicyVersion", "v1"); input.put("kind", kind + "_RELATIONS");
        input.put("sourceType", "NODE"); input.put("sourceId", Long.toString(nodeId));
        input.put("scope", scope.canonical()); input.put("items", items); return AiCanonicalJson.hash(input);
    }

    public Map<String,Object> evidence(List<AiEvidenceCheck> checks) {
        List<Map<String,Object>> results=new ArrayList<>();
        for(var check:checks) {
            Map<String,Object> result=new LinkedHashMap<>(); result.put("evidenceId",check.evidenceId());
            try {
                var source=source(check.type(),check.sourceId(),null,check.scope());
                if(!"AVAILABLE".equals(source.get("status"))) {result.put("status","UNAVAILABLE");}
                else if(!check.usage().equals("RELATION") && !"v1".equals(check.text("hashPolicyVersion"))) {
                    result.put("status","NOT_COMPARABLE");result.put("reason","HASH_POLICY_MISMATCH");
                } else {
                    result.put("status","AVAILABLE");result.put("currentRevision",source.get("sourceRevision"));
                    if(check.usage().equals("DOCUMENT")) result.put("contentChanged",!source.get("documentHash").equals(check.text("documentHash")));
                    else {
                        String kind=check.text("relationType");
                        var items=relationItems(check.sourceId(),kind,check.scope());
                        if(check.usage().equals("RELATION_LIST")) result.put("relationsChanged",!relationHash(check.sourceId(),kind,check.scope(),items).equals(check.text("relationsHash")));
                        else {
                            String targetType=kind.equals("LINKED_NOTES")?"NOTE":"EDGE";
                            long targetId=AiScope.id(check.text(kind.equals("LINKED_NOTES")?"targetSourceId":"edgeId"));
                            if(!allowed(targetType,targetId,check.scope())) {
                                result.remove("currentRevision");result.put("status","UNAVAILABLE");
                            } else {
                                Map<String,Object> expected=new LinkedHashMap<>();
                                if(kind.equals("LINKED_NOTES")) expected.put("noteId",Long.toString(targetId));
                                else {expected.put("edgeId",Long.toString(targetId));expected.put("sourceNodeId",check.text("sourceNodeId"));expected.put("targetNodeId",check.text("targetNodeId"));}
                                result.put("relationExists",items.contains(expected));
                            }
                        }
                    }
                }
            } catch(AiApiException error) {
                result.clear();result.put("evidenceId",check.evidenceId());
                result.put("status",error.status==404?"UNAVAILABLE":"CHECK_FAILED");
            }
            // DB 공통 장애는 밖으로 전파한다. 중단된 트랜잭션에서 부분 결과를 성공으로 반환하지 않는다.
            results.add(result);
        }
        return Map.of("results",results);
    }
}
