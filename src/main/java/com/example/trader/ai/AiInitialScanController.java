package com.example.trader.ai;

import java.util.*;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** 원본 ID 목록만 읽는다. 운영자가 만든 실행을 AI가 임의 생성/활성화할 수 없다. */
@RestController
@RequestMapping("/internal/ai/indexing/initial-runs")
public class AiInitialScanController {
    private final JdbcTemplate jdbc;
    private final AiCursorCodec codec;
    public AiInitialScanController(JdbcTemplate jdbc,AiCursorCodec codec) { this.jdbc=jdbc;this.codec=codec; }

    @GetMapping("/{id}/sources")
    @Transactional(readOnly=true,timeout=5)
    public Map<String,Object> sources(@PathVariable UUID id,@RequestParam String sourceType,
            @RequestParam(required=false) String cursor,@RequestParam(required=false) String checkpoint,
            @RequestParam(defaultValue="100") int pageSize) {
        if(!Set.of("NOTE","NODE").contains(sourceType) || pageSize<1 || pageSize>500
                || (cursor!=null && checkpoint!=null)) throw AiApiException.invalid();
        var runs=jdbc.queryForList("SELECT * FROM ai_initial_run WHERE id=?",id);
        if(runs.isEmpty()) throw new AiApiException(404,"INITIAL_RUN_UNAVAILABLE");
        var run=runs.get(0);
        if(!"ACTIVE".equals(run.get("status")) || !((java.sql.Timestamp)run.get("expires_at")).toInstant().isAfter(Instant.now()))
            throw new AiApiException(409,"INITIAL_RUN_INACTIVE");
        long upper=((Number)run.get(sourceType.equals("NOTE")?"note_upper_id":"node_upper_id")).longValue();
        var binding=Map.<String,Object>of("purpose","INITIAL_PAGE","runId",id.toString(),"sourceType",sourceType,
                "upperId",Long.toString(upper),"pageSize",pageSize);
        var progress=Map.<String,Object>of("purpose","INITIAL_CHECKPOINT","runId",id.toString(),"sourceType",sourceType,
                "upperId",Long.toString(upper),"pageSize",pageSize);
        long last=cursor!=null?codec.decode(cursor,binding,true):checkpoint!=null?codec.decode(checkpoint,progress,true):0;
        var rows=jdbc.queryForList("""
            SELECT source_id,revision FROM search_source_state WHERE source_type=?
              AND source_id>? AND source_id<=? AND NOT deleted ORDER BY source_id LIMIT ?
            """,sourceType,last,upper,pageSize+1);
        boolean done=rows.size()<=pageSize;
        var selected=rows.subList(0,Math.min(rows.size(),pageSize));
        List<Map<String,Object>> items=new ArrayList<>();
        for(var row:selected) items.add(Map.of("sourceType",sourceType,"sourceId",row.get("source_id").toString(),
                "sourceRevision",row.get("revision").toString()));
        long end=selected.isEmpty()?last:((Number)selected.get(selected.size()-1).get("source_id")).longValue();
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("runId",id.toString());result.put("sourceType",sourceType);result.put("items",items);
        result.put("fromId",Long.toString(last));result.put("lastId",Long.toString(end));result.put("completed",done);
        result.put("occurredAt",((java.sql.Timestamp)run.get("created_at")).toInstant().toString());
        result.put("checkpoint",codec.encode(progress,end,86400));
        result.put("terminalProof",done?codec.encode(Map.of("purpose","INITIAL_DONE","runId",id.toString(),"sourceType",sourceType),end,86400):null);
        result.put("nextCursor",done?null:codec.encode(binding,end,900));
        return result;
    }

    @PostMapping("/{id}/completion")
    @Transactional(timeout=5)
    public Map<String,Object> completed(@PathVariable UUID id,@RequestBody Map<String,String> proofs) {
        if(!proofs.keySet().equals(Set.of("NOTE","NODE"))) throw AiApiException.invalid();
        long note=codec.decode(proofs.get("NOTE"),Map.of("purpose","INITIAL_DONE","runId",id.toString(),"sourceType","NOTE"),true);
        long node=codec.decode(proofs.get("NODE"),Map.of("purpose","INITIAL_DONE","runId",id.toString(),"sourceType","NODE"),true);
        var rows=jdbc.queryForList("SELECT * FROM ai_initial_run WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new AiApiException(404,"INITIAL_RUN_UNAVAILABLE");
        var row=rows.get(0);
        if("ABORTED".equals(row.get("status"))) throw new AiApiException(409,"INITIAL_RUN_INACTIVE");
        String fingerprint;
        try {
            fingerprint=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest((id+":"+note+":"+node).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch(Exception e) {throw new IllegalStateException(e);}
        if(row.get("completion_hash")!=null && !fingerprint.equals(row.get("completion_hash")))
            throw new AiApiException(409,"INITIAL_REPORT_CONFLICT");
        boolean active=((java.sql.Timestamp)row.get("expires_at")).toInstant().isAfter(Instant.now());
        jdbc.update("""
            UPDATE ai_initial_run SET completion_hash=?,completed_at=coalesce(completed_at,clock_timestamp()),
              status=CASE WHEN expires_at>clock_timestamp() THEN 'COMPLETED' ELSE 'EXPIRED' END WHERE id=?
            """,fingerprint,id);
        return Map.of("status",active?"CONFIRMED":"RECORDED_LATE","meaning","SCAN_ACCEPTED_NOT_INDEX_READY");
    }
}
