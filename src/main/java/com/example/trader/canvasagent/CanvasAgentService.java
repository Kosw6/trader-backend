package com.example.trader.canvasagent;

import com.example.trader.dto.map.CreateNodeRequest;
import com.example.trader.dto.map.RequestEdgeDto;
import com.example.trader.dto.map.UpdateTeamNodeContentRequest;
import com.example.trader.service.NodeService;
import com.example.trader.service.EdgeService;
import com.example.trader.service.TeamNodePositionWriter;
import com.example.trader.service.NodeCacheService;
import com.example.trader.service.GraphCacheService;
import com.example.trader.ws.raw.lock.CanvasLockService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Bounded reversible AI edit transaction. Membership is rechecked on every call. */
@Service
@RequiredArgsConstructor
public class CanvasAgentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final NodeService nodes;
    private final EdgeService edges;
    private final EntityManager entityManager;
    private final TeamNodePositionWriter positions;
    private final CanvasLockService locks;
    private final NodeCacheService nodeCache;
    private final GraphCacheService graphCache;

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
        if (request.nodes().isEmpty() && request.edges().isEmpty() && request.edits().isEmpty()) throw error(HttpStatus.BAD_REQUEST,"CANVAS_EMPTY_EDIT");
        Set<String> keys=new HashSet<>();
        Map<Long,JsonNode> originals=new HashMap<>();
        for (JsonNode node:parse(before).path("nodes")) {
            keys.add(node.path("id").asText());originals.put(node.path("id").asLong(),node);
        }
        if(request.edits().size()>6) throw error(HttpStatus.BAD_REQUEST,"CANVAS_EDIT_LIMIT");
        Set<Long> edited=new HashSet<>();List<Map<String,Object>> backups=new ArrayList<>();
        List<CanvasAgentContracts.NodeEdit> changes=new ArrayList<>();
        for(var patch:request.edits()) {
            JsonNode original=originals.get(patch.nodeId());
            if(original==null || !edited.add(patch.nodeId())) throw error(HttpStatus.BAD_REQUEST,"CANVAS_INVALID_EDIT");
            validatePatch(patch);
            Map<String,Object> backup=new LinkedHashMap<>();backup.put("nodeId",patch.nodeId());
            String subject=null,content=null;Double x=null,y=null;
            if(patch.subject()!=null && !patch.subject().equals(original.path("subject").asText(null))) {
                if(original.path("subject").isNull()) throw error(HttpStatus.CONFLICT,"CANVAS_ORIGINAL_NULL_REVIEW");
                subject=patch.subject();backup.put("subject",original.path("subject").asText());
            }
            if(patch.content()!=null && !patch.content().equals(original.path("content").asText(null))) {
                if(original.path("content").isNull()) throw error(HttpStatus.CONFLICT,"CANVAS_ORIGINAL_NULL_REVIEW");
                content=patch.content();backup.put("content",original.path("content").asText());
            }
            if(patch.x()!=null && (Double.compare(patch.x(),original.path("x").asDouble())!=0 || Double.compare(patch.y(),original.path("y").asDouble())!=0)) {
                x=patch.x();y=patch.y();backup.put("x",original.path("x").asDouble());backup.put("y",original.path("y").asDouble());
            }
            if(backup.size()>1) {backups.add(backup);changes.add(new CanvasAgentContracts.NodeEdit(patch.nodeId(),subject,content,x,y));}
        }
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
        if(request.nodes().isEmpty() && request.edges().isEmpty() && changes.isEmpty()) throw error(HttpStatus.BAD_REQUEST,"CANVAS_EMPTY_EDIT");
        for(var patch:changes) acquireEditLock(user,team,page,patch.nodeId());
        for(var patch:changes) writePatch(user,team,page,patch);
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
        entityManager.flush();
        for(var patch:changes) requireEditLock(user,team,page,patch.nodeId());
        String after=fingerprint(capture(team,page));
        var receipt=Map.of("nodeIds",nodeIds,"edgeIds",edgeIds,"updatedNodeIds",changes.stream().map(CanvasAgentContracts.NodeEdit::nodeId).toList(),
                "beforeAvailable",!backups.isEmpty(),"model","claude-sonnet-5-5","afterFingerprint",after);
        jdbc.update("UPDATE canvas_ai_run SET status='APPLIED',receipt=?::jsonb,before_state=?::jsonb,after_fingerprint=?,completed_at=now() WHERE id=?",
                encode(receipt),encode(backups),after,request.requestId());
        evictAfterCommit(page);
        return result(request.requestId(),row(user,team,page,request.requestId()));
    }
    private static long resolve(String key,Map<String,Long> replacements) {
        return replacements.containsKey(key)?replacements.get(key):Long.parseLong(key);
    }

    private void validatePatch(CanvasAgentContracts.NodeEdit patch) {
        if(patch.nodeId()<=0 || (patch.subject()==null && patch.content()==null && patch.x()==null && patch.y()==null)
                || (patch.subject()!=null && (patch.subject().isBlank() || patch.subject().length()>120))
                || (patch.content()!=null && patch.content().length()>6000)
                || (patch.x()==null)!=(patch.y()==null)
                || (patch.x()!=null && (!Double.isFinite(patch.x()) || !Double.isFinite(patch.y()) || Math.abs(patch.x())>100000 || Math.abs(patch.y())>100000)))
            throw error(HttpStatus.BAD_REQUEST,"CANVAS_INVALID_EDIT");
    }
    private void acquireEditLock(long user,long team,long page,long node) {
        if(!locks.tryAcquire(team,page,node,user)) throw error(HttpStatus.CONFLICT,"CANVAS_NODE_BUSY");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                try {locks.release(team,page,node,user);} catch(RuntimeException ignored) { /* bounded lease expires */ }
            }
        });
    }
    private void requireEditLock(long user,long team,long page,long node) {
        if(!locks.isHeldBy(team,page,node,user)) throw error(HttpStatus.CONFLICT,"CANVAS_NODE_LEASE_EXPIRED");
    }
    private void writePatch(long user,long team,long page,CanvasAgentContracts.NodeEdit patch) {
        requireEditLock(user,team,page,patch.nodeId());
        if(patch.subject()!=null || patch.content()!=null) {
            var dto=new UpdateTeamNodeContentRequest();List<String> fields=new ArrayList<>();
            if(patch.subject()!=null) {dto.setSubject(patch.subject());fields.add("subject");}
            if(patch.content()!=null) {dto.setContent(patch.content());fields.add("content");}
            dto.setDirtyFields(fields);dto.setBaseVersion(jdbc.queryForObject("SELECT version FROM node WHERE id=? AND page_id=?",Integer.class,patch.nodeId(),page));
            nodes.updateTeamNode(team,page,patch.nodeId(),user,dto);
            entityManager.flush();
        }
        if(patch.x()!=null) {
            requireEditLock(user,team,page,patch.nodeId());
            int version=jdbc.queryForObject("SELECT version FROM node WHERE id=? AND page_id=?",Integer.class,patch.nodeId(),page);
            if(positions.write(team,page,patch.nodeId(),user,version,patch.x(),patch.y()).isEmpty())
                throw error(HttpStatus.CONFLICT,"CANVAS_CHANGED");
        }
    }
    private void evictAfterCommit(long page) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {nodeCache.evictPageNodes(page);} catch(RuntimeException ignored) {}
                try {graphCache.evictGraph(page);} catch(RuntimeException ignored) {}
            }
        });
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
        String saved=jdbc.queryForObject("SELECT before_state::text FROM canvas_ai_run WHERE id=?",String.class,id);
        JsonNode backups=saved==null?mapper.createArrayNode():parse(saved);
        Set<Long> expected=new HashSet<>(),actual=new HashSet<>();
        for(JsonNode node:receipt.path("updatedNodeIds")) expected.add(node.asLong());
        for(JsonNode node:backups) actual.add(node.path("nodeId").asLong());
        if(!backups.isArray() || backups.size()>6 || actual.size()!=backups.size() || !expected.equals(actual))
            throw error(HttpStatus.CONFLICT,"CANVAS_BACKUP_INVALID");
        for(Long node:actual) acquireEditLock(user,team,page,node);
        for(JsonNode node:receipt.path("nodeIds")) acquireEditLock(user,team,page,node.asLong());
        for(JsonNode node:backups) {
            var patch=new CanvasAgentContracts.NodeEdit(node.path("nodeId").asLong(),
                    node.has("subject")?node.path("subject").asText():null,node.has("content")?node.path("content").asText():null,
                    node.has("x")?node.path("x").asDouble():null,node.has("y")?node.path("y").asDouble():null);
            // Restoration may be longer than new AI output. Never truncate original content.
            writePatch(user,team,page,patch);
        }
        for (JsonNode edge:receipt.path("edgeIds")) edges.deleteTeamEdge(team,page,edge.asLong());
        for (JsonNode node:receipt.path("nodeIds")) nodes.deleteTeamNode(team,page,node.asLong());
        entityManager.flush();
        for(Long node:actual) requireEditLock(user,team,page,node);
        jdbc.update("UPDATE canvas_ai_run SET status='UNDONE',completed_at=now() WHERE id=?",id);
        evictAfterCommit(page);
        return result(id,row(user,team,page,id));
    }
}
