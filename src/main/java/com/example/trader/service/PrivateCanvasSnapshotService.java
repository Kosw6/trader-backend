package com.example.trader.service;

import com.example.trader.entity.*;
import com.example.trader.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PrivateCanvasSnapshotService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final UserRepository users;
    private final DirectoryRepository directories;
    private final PageRepository pages;
    private final NodeRepository nodes;
    private final EdgeRepository edges;

    public record SaveRequest(UUID requestId, long teamId, long pageId, String title, UUID replaceId) {}
    public record CopyRequest(UUID requestId) {}
    public record CopyResult(long pageId, long directoryId) {}
    public record Summary(UUID id, long teamId, String teamName, String pageTitle, String title,
                          int slot, Instant createdAt, int nodeCount, int edgeCount) {}
    public record Detail(Summary summary, JsonNode graph) {}

    // ONE SQL statement / MVCC snapshot. Never copy linked private notes or attachments.
    static final String CAPTURE = """
        SELECT t.team_name, p.title AS page_title, jsonb_build_object(
          'schemaVersion',1,
          'nodes',COALESCE((SELECT jsonb_agg(jsonb_build_object(
            'id',n.id,'x',n.x,'y',n.y,'subject',n.subject,'content',n.content,
            'symb',n.symb,'recordDate',n.record_date) ORDER BY n.id)
            FROM node n WHERE n.page_id=p.id),'[]'::jsonb),
          'edges',COALESCE((SELECT jsonb_agg(jsonb_build_object(
            'id',e.id,'sourceId',e.source_id,'targetId',e.target_id,'type',e.type,
            'label',e.label,'sourceHandle',e.source_handle,'targetHandle',e.target_handle,
            'variant',e.variant,'animated',e.animated,'stroke',e.stroke,'strokeWidth',e.stroke_width)
            ORDER BY e.id) FROM edge e WHERE e.page_id=p.id),'[]'::jsonb)
        )::text AS graph
        FROM page p JOIN directory d ON d.id=p.directory_id JOIN team t ON t.id=d.team_id
        WHERE p.id=? AND t.id=? AND EXISTS
          (SELECT 1 FROM user_team ut WHERE ut.team_id=t.id AND ut.user_id=?)
        """;
    private static final String SUMMARY = """
        SELECT id,source_team_id,team_name,page_title,title,slot,created_at,
        jsonb_array_length(graph->'nodes') AS node_count,
        jsonb_array_length(graph->'edges') AS edge_count
        FROM private_canvas_snapshot WHERE owner_id=?
        """;

    static String title(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 120)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이름은 1~120자로 입력해주세요.");
        return value.strip();
    }
    static int freeSlot(Collection<Integer> used) {
        for (int i=1;i<=5;i++) if (!used.contains(i)) return i;
        throw new ResponseStatusException(HttpStatus.CONFLICT, "그룹별 5개까지 저장할 수 있어요. 교체할 저장본을 선택해주세요.");
    }
    private void lockOwner(long owner) {
        // Database lock, shared across backend instances. READ COMMITTED sees preceding commit.
        if (jdbc.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, owner).isEmpty())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    private Summary summary(ResultSet rs, int index) throws SQLException {
        return new Summary(UUID.fromString(rs.getString("id")), rs.getLong("source_team_id"),
            rs.getString("team_name"), rs.getString("page_title"), rs.getString("title"),
            rs.getInt("slot"), rs.getTimestamp("created_at").toInstant(),
            rs.getInt("node_count"), rs.getInt("edge_count"));
    }
    private JsonNode parse(String json) {
        try { return mapper.readTree(json); }
        catch (Exception e) { throw new IllegalStateException("Invalid snapshot payload", e); }
    }
    @Transactional(readOnly=true)
    public List<Summary> list(long owner, Long teamId) {
        // No group join: library survives departure, source-page and group deletion.
        return teamId == null ? jdbc.query(SUMMARY+" ORDER BY created_at DESC", this::summary, owner)
            : jdbc.query(SUMMARY+" AND source_team_id=? ORDER BY created_at DESC", this::summary, owner, teamId);
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Detail get(long owner, UUID id) {
        var found=jdbc.query(SUMMARY+" AND id=?", this::summary, owner,id);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String graph=jdbc.queryForObject("SELECT graph::text FROM private_canvas_snapshot WHERE owner_id=? AND id=?",String.class,owner,id);
        return new Detail(found.get(0),parse(graph));
    }
    @Transactional
    public Detail save(long owner, SaveRequest request) {
        if(request==null || request.requestId()==null || request.teamId()<=0 || request.pageId()<=0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String name=title(request.title());
        lockOwner(owner);
        var retried=jdbc.queryForList("SELECT source_team_id,source_page_id,title FROM private_canvas_snapshot WHERE owner_id=? AND id=?",owner,request.requestId());
        if(!retried.isEmpty()) {
            var prior=retried.get(0);
            if(((Number)prior.get("source_team_id")).longValue()!=request.teamId()
                || ((Number)prior.get("source_page_id")).longValue()!=request.pageId()
                || !name.equals(prior.get("title"))) throw new ResponseStatusException(HttpStatus.CONFLICT);
            return get(owner,request.requestId());
        }
        var captures=jdbc.queryForList(CAPTURE,request.pageId(),request.teamId(),owner);
        if(captures.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var capture=captures.get(0);
        String raw=(String)capture.get("graph"); JsonNode graph=parse(raw);
        if(raw.getBytes(StandardCharsets.UTF_8).length>4*1024*1024 || graph.path("nodes").size()>2000 || graph.path("edges").size()>5000)
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"스냅샷 크기 제한을 초과했어요.");
        int slot;
        if(request.replaceId()!=null) {
            var slots=jdbc.queryForList("SELECT slot FROM private_canvas_snapshot WHERE owner_id=? AND source_team_id=? AND id=?",Integer.class,owner,request.teamId(),request.replaceId());
            if(slots.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            slot=slots.get(0);
            jdbc.update("DELETE FROM private_canvas_snapshot WHERE owner_id=? AND id=?",owner,request.replaceId());
        } else slot=freeSlot(jdbc.queryForList("SELECT slot FROM private_canvas_snapshot WHERE owner_id=? AND source_team_id=?",Integer.class,owner,request.teamId()));
        jdbc.update("INSERT INTO private_canvas_snapshot(id,owner_id,source_team_id,source_page_id,slot,title,team_name,page_title,graph) VALUES (?,?,?,?,?,?,?,?,?::jsonb)",
            request.requestId(),owner,request.teamId(),request.pageId(),slot,name,capture.get("team_name"),capture.get("page_title"),raw);
        return get(owner,request.requestId());
    }
    @Transactional
    public void delete(long owner, UUID id) {
        lockOwner(owner);
        if(jdbc.update("DELETE FROM private_canvas_snapshot WHERE owner_id=? AND id=?",owner,id)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @Transactional
    public CopyResult copy(long owner, UUID id, CopyRequest request) {
        if(request==null || request.requestId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        lockOwner(owner);
        Detail detail=get(owner,id);
        var retried=jdbc.queryForList("SELECT snapshot_id,page_id,directory_id FROM private_canvas_snapshot_copy WHERE owner_id=? AND request_id=?",owner,request.requestId());
        if(!retried.isEmpty()) {
            var row=retried.get(0);
            if(!id.toString().equals(row.get("snapshot_id").toString())) throw new ResponseStatusException(HttpStatus.CONFLICT);
            return new CopyResult(((Number)row.get("page_id")).longValue(),((Number)row.get("directory_id")).longValue());
        }
        User user=users.getReferenceById(owner);
        Directory dir=directories.save(Directory.builder().name("스냅샷 복사본").user(user).build());
        Page page=pages.save(Page.builder().title(detail.summary().title()).user(user).directory(dir).build());
        Map<Long,Node> replacements=new HashMap<>();
        for(JsonNode n:detail.graph().path("nodes")) {
            Node node=Node.builder().page(page).x(n.path("x").asDouble()).y(n.path("y").asDouble())
                .subject(text(n,"subject")).content(text(n,"content")).symb(text(n,"symb"))
                .recordDate(n.path("recordDate").isTextual()?LocalDate.parse(n.get("recordDate").asText()):null).build();
            replacements.put(n.path("id").asLong(),nodes.save(node));
        }
        for(JsonNode e:detail.graph().path("edges")) {
            Node source=replacements.get(e.path("sourceId").asLong()),target=replacements.get(e.path("targetId").asLong());
            if(source==null || target==null) throw new IllegalStateException("Invalid snapshot edge endpoint");
            edges.save(Edge.builder().page(page).source(source).target(target).type(text(e,"type"))
                .label(text(e,"label")).sourceHandle(text(e,"sourceHandle")).targetHandle(text(e,"targetHandle"))
                .variant(text(e,"variant")).animated(e.path("animated").asBoolean()).stroke(text(e,"stroke"))
                .strokeWidth(e.path("strokeWidth").isNumber()?e.get("strokeWidth").asInt():null).build());
        }
        jdbc.update("INSERT INTO private_canvas_snapshot_copy(owner_id,request_id,snapshot_id,page_id,directory_id) VALUES (?,?,?,?,?)",owner,request.requestId(),id,page.getId(),dir.getId());
        return new CopyResult(page.getId(),dir.getId());
    }
    private static String text(JsonNode node,String key) { return node.path(key).isNull() || node.path(key).isMissingNode()?null:node.path(key).asText(); }
}
