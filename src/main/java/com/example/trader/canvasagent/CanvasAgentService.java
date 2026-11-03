package com.example.trader.canvasagent;

import com.example.trader.dto.map.CreateNodeRequest;
import com.example.trader.dto.map.RequestEdgeDto;
import com.example.trader.service.NodeService;
import com.example.trader.service.EdgeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Bounded, append-only AI edit transaction. Membership is rechecked on every call. */
@Service
@RequiredArgsConstructor
public class CanvasAgentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final NodeService nodes;
    private final EdgeService edges;
    private final EntityManager entityManager;

    static final String CAPTURE = """
        SELECT jsonb_build_object(
          'nodes',COALESCE((SELECT jsonb_agg(jsonb_build_object(
            'id',n.id::text,'x',n.x,'y',n.y,'subject',n.subject,'content',n.content,
            'symb',n.symb,'recordDate',n.record_date,'version',n.version,
            'lockVersion',n.lock_version) ORDER BY n.id) FROM node n WHERE n.page_id=p.id),'[]'::jsonb),
          'edges',COALESCE((SELECT jsonb_agg(to_jsonb(e) ORDER BY e.id)
            FROM edge e WHERE e.page_id=p.id),'[]'::jsonb))::text
        FROM page p JOIN directory d ON d.id=p.directory_id
        WHERE p.id=? AND d.team_id=?
        """;

    private void authorize(long user, long team, long page, boolean lock) {
        if (user<=0 || team<=0 || page<=0) throw error(HttpStatus.BAD_REQUEST,"INVALID_SCOPE");
        var members=jdbc.queryForList("SELECT user_id FROM user_team WHERE user_id=? AND team_id=?"
                +(lock?" FOR KEY SHARE":""),Long.class,user,team);
        if (members.isEmpty()) throw error(HttpStatus.FORBIDDEN,"CANVAS_ACCESS_DENIED");
        var pages=jdbc.queryForList("SELECT p.id FROM page p JOIN directory d ON d.id=p.directory_id "
                +"WHERE p.id=? AND d.team_id=?"+(lock?" FOR UPDATE OF p":""),Long.class,page,team);
        if (pages.isEmpty()) throw error(HttpStatus.FORBIDDEN,"CANVAS_ACCESS_DENIED");
        if (lock) {
            jdbc.queryForList("SELECT id FROM node WHERE page_id=? ORDER BY id FOR UPDATE",Long.class,page);
            jdbc.queryForList("SELECT id FROM edge WHERE page_id=? ORDER BY id FOR UPDATE",Long.class,page);
        }
    }
    private String capture(long team,long page) {
        return jdbc.queryForObject(CAPTURE,String.class,page,team);
    }
    static String fingerprint(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("CANVAS_HASH_FAILED"); }
    }
    private JsonNode parse(String raw) {
        try { return mapper.readTree(raw); }
        catch (Exception e) { throw new IllegalStateException("CANVAS_RECEIPT_INVALID"); }
    }
    private String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("CANVAS_RECEIPT_INVALID"); }
    }
    private static ResponseStatusException error(HttpStatus status,String code) {
        return new ResponseStatusException(status,code);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> snapshot(long user,long team,long page) {
        authorize(user,team,page,false);
        String raw=capture(team,page);JsonNode graph=parse(raw);
        if (graph.path("nodes").size()>80 || graph.path("edges").size()>120
                || raw.getBytes(StandardCharsets.UTF_8).length>180000)
            throw error(HttpStatus.PAYLOAD_TOO_LARGE,"CANVAS_AGENT_SCOPE_TOO_LARGE");
        // Existing node bodies are bounded previews; linked private notes are not included.
        for (JsonNode n:graph.path("nodes")) {
            String text=n.path("content").asText("");
            ((com.fasterxml.jackson.databind.node.ObjectNode)n).put("content",text.substring(0,Math.min(text.length(),300)));
        }
        return Map.of("fingerprint",fingerprint(raw),"graph",graph,"existingContentIsPreview",true);
    }

    private Map<String,Object> row(long user,long team,long page,UUID id) {
        var found=jdbc.queryForList("SELECT status,goal_hash,base_fingerprint,receipt::text AS receipt,after_fingerprint, "
                +"expires_at<clock_timestamp() AS expired "
                +"FROM canvas_ai_run WHERE id=? AND owner_id=? AND team_id=? AND page_id=?",id,user,team,page);
        if (found.isEmpty()) throw error(HttpStatus.NOT_FOUND,"CANVAS_RUN_NOT_FOUND");
        return found.get(0);
    }
    private Map<String,Object> result(UUID id,Map<String,Object> row) {
        Object status="CLAIMED".equals(row.get("status")) && Boolean.TRUE.equals(row.get("expired"))?"EXPIRED":row.get("status");
        return Map.of("runId",id,"status",status,"goalHash",row.get("goal_hash"),"receipt",
                row.get("receipt")==null?Map.of():parse((String)row.get("receipt")));
    }

    @Transactional(readOnly=true)
    public Map<String,Object> status(long user,long team,long page,UUID id) {
        authorize(user,team,page,false);return result(id,row(user,team,page,id));
    }

    @Transactional
    public Map<String,Object> claim(long user,long team,long page,CanvasAgentContracts.Claim request) {
        authorize(user,team,page,true);
        jdbc.update("UPDATE canvas_ai_run SET status='EXPIRED',completed_at=now() "
                +"WHERE page_id=? AND status='CLAIMED' AND expires_at<clock_timestamp()",page);
        var prior=jdbc.queryForList("SELECT id FROM canvas_ai_run WHERE id=?",request.requestId());
        if (!prior.isEmpty()) {
            var value=row(user,team,page,request.requestId());
            if (!request.goalHash().equals(value.get("goal_hash"))
                    || !request.baseFingerprint().equals(value.get("base_fingerprint")))
                throw error(HttpStatus.CONFLICT,"CANVAS_REQUEST_ID_REUSED");
            return result(request.requestId(),value);
        }
        if (!fingerprint(capture(team,page)).equals(request.baseFingerprint()))
            throw error(HttpStatus.CONFLICT,"CANVAS_CHANGED");
        if (!jdbc.queryForList("SELECT id FROM canvas_ai_run WHERE page_id=? AND status='CLAIMED'",UUID.class,page).isEmpty())
            throw error(HttpStatus.CONFLICT,"CANVAS_AGENT_BUSY");
        jdbc.update("INSERT INTO canvas_ai_run(id,owner_id,team_id,page_id,goal_hash,base_fingerprint,status) VALUES (?,?,?,?,?,?,'CLAIMED')",
                request.requestId(),user,team,page,request.goalHash(),request.baseFingerprint());
        return Map.of("runId",request.requestId(),"status","NEW");
    }

    @Transactional
    public Map<String,Object> apply(long user,long team,long page,CanvasAgentContracts.Apply request) {
        authorize(user,team,page,true);var prior=row(user,team,page,request.requestId());
        if ("APPLIED".equals(prior.get("status"))) return result(request.requestId(),prior);
        if (!"CLAIMED".equals(prior.get("status")) || !request.baseFingerprint().equals(prior.get("base_fingerprint")))
            throw error(HttpStatus.CONFLICT,"CANVAS_RUN_NOT_ACTIVE");
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT expires_at<clock_timestamp() FROM canvas_ai_run WHERE id=?",Boolean.class,request.requestId())))
            throw error(HttpStatus.CONFLICT,"CANVAS_RUN_EXPIRED");
        String before=capture(team,page);
        if (!fingerprint(before).equals(request.baseFingerprint())) throw error(HttpStatus.CONFLICT,"CANVAS_CHANGED");
        if (request.nodes().isEmpty() && request.edges().isEmpty()) throw error(HttpStatus.BAD_REQUEST,"CANVAS_EMPTY_EDIT");
        Set<String> keys=new HashSet<>();
        for (JsonNode node:parse(before).path("nodes")) keys.add(node.path("id").asText());
        Set<String> added=new HashSet<>();
        for (var node:request.nodes()) {
            if (!Double.isFinite(node.x()) || !Double.isFinite(node.y()) || !added.add(node.key()))
                throw error(HttpStatus.BAD_REQUEST,"CANVAS_INVALID_NODE");
        }
        keys.addAll(added);Set<String> pairs=new HashSet<>();
        for (var edge:request.edges()) {
            if (!keys.contains(edge.source()) || !keys.contains(edge.target()) || edge.source().equals(edge.target())
                    || !pairs.add(edge.source()+":"+edge.target())) throw error(HttpStatus.BAD_REQUEST,"CANVAS_INVALID_EDGE");
        }
        Map<String,Long> replacements=new HashMap<>();List<Long> nodeIds=new ArrayList<>(),edgeIds=new ArrayList<>();
        for (var node:request.nodes()) {
            var dto=new CreateNodeRequest();dto.setSubject(node.subject());dto.setContent(node.content());
            dto.setSymb(node.symb());dto.setX(node.x());dto.setY(node.y());
            dto.setRecordDate(LocalDate.now(ZoneId.of("Asia/Seoul")));
            var saved=nodes.createTeamNode(dto,team,page,user);replacements.put(node.key(),saved.getId());nodeIds.add(saved.getId());
        }
        for (var edge:request.edges()) {
            var dto=new RequestEdgeDto();dto.setSourceId(resolve(edge.source(),replacements));dto.setTargetId(resolve(edge.target(),replacements));
            dto.setLabel(edge.label());dto.setType("default");dto.setSourceHandle("rightS");dto.setTargetHandle("leftT");
            dto.setStroke("#7c9cff");dto.setStrokeWidth(2);
            edgeIds.add(edges.createTeamEdge(team,page,dto).getId());
        }
        entityManager.flush();String after=fingerprint(capture(team,page));
        var receipt=Map.of("nodeIds",nodeIds,"edgeIds",edgeIds,"model","claude-sonnet-5-5","afterFingerprint",after);
        jdbc.update("UPDATE canvas_ai_run SET status='APPLIED',receipt=?::jsonb,after_fingerprint=?,completed_at=now() WHERE id=?",
                encode(receipt),after,request.requestId());
        return result(request.requestId(),row(user,team,page,request.requestId()));
    }
    private static long resolve(String key,Map<String,Long> replacements) {
        return replacements.containsKey(key)?replacements.get(key):Long.parseLong(key);
    }

    @Transactional
    public Map<String,Object> cancel(long user,long team,long page,UUID id) {
        authorize(user,team,page,true);row(user,team,page,id);
        jdbc.update("UPDATE canvas_ai_run SET status='CANCELLED',completed_at=now() WHERE id=? AND status='CLAIMED'",id);
        return result(id,row(user,team,page,id));
    }

    @Transactional
    public Map<String,Object> undo(long user,long team,long page,UUID id) {
        authorize(user,team,page,true);var value=row(user,team,page,id);
        if ("UNDONE".equals(value.get("status"))) return result(id,value);
        if (!"APPLIED".equals(value.get("status"))) throw error(HttpStatus.CONFLICT,"CANVAS_RUN_NOT_APPLIED");
        if (!fingerprint(capture(team,page)).equals(value.get("after_fingerprint")))
            throw error(HttpStatus.CONFLICT,"CANVAS_CHANGED_AFTER_AI_EDIT");
        JsonNode receipt=parse((String)value.get("receipt"));
        for (JsonNode edge:receipt.path("edgeIds")) edges.deleteTeamEdge(team,page,edge.asLong());
        for (JsonNode node:receipt.path("nodeIds")) nodes.deleteTeamNode(team,page,node.asLong());
        entityManager.flush();
        jdbc.update("UPDATE canvas_ai_run SET status='UNDONE',completed_at=now() WHERE id=?",id);
        return result(id,row(user,team,page,id));
    }
}
