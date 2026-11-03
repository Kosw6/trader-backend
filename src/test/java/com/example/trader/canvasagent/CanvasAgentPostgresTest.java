package com.example.trader.canvasagent;

import com.example.trader.dto.map.ResponseEdgeDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.service.EdgeService;
import com.example.trader.service.NodeService;
import com.example.trader.service.TeamNodePositionWriter;
import com.example.trader.service.NodeCacheService;
import com.example.trader.service.GraphCacheService;
import com.example.trader.ws.raw.lock.CanvasLockService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="AI_CONTRACT_TEST_DB",matches="local-isolated")
class CanvasAgentPostgresTest {
    JdbcTemplate db;TransactionTemplate tx;CanvasAgentService service;String schema;
    NodeService nodes;EdgeService edges;AtomicLong nodeIds=new AtomicLong(100),edgeIds=new AtomicLong(200);
    TeamNodePositionWriter positions;CanvasLockService locks;

    @BeforeEach void setup() throws Exception {
        schema="canvas_agent_"+UUID.randomUUID().toString().replace("-","");
        var base=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55439/postgres","ai_contract","");
        new JdbcTemplate(base).execute("CREATE SCHEMA "+schema);
        var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55439/postgres?currentSchema="+schema,"ai_contract","");
        db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("""
            CREATE TABLE users(id bigint PRIMARY KEY);
            CREATE TABLE team(id bigint PRIMARY KEY);
            CREATE TABLE user_team(user_id bigint,team_id bigint REFERENCES team(id));
            CREATE TABLE directory(id bigint PRIMARY KEY,team_id bigint REFERENCES team(id));
            CREATE TABLE page(id bigint PRIMARY KEY,directory_id bigint REFERENCES directory(id));
            CREATE TABLE node(id bigint PRIMARY KEY,page_id bigint REFERENCES page(id),x float8,y float8,
              subject text,content text,symb text,record_date date,version integer DEFAULT 0,lock_version bigint DEFAULT 0);
            CREATE TABLE edge(id bigint PRIMARY KEY,page_id bigint REFERENCES page(id),source_id bigint,target_id bigint,label text);
            INSERT INTO users VALUES(1),(2),(3);INSERT INTO team VALUES(7),(8);
            INSERT INTO user_team VALUES(1,7),(2,7),(3,8);
            INSERT INTO directory VALUES(1,7),(2,8);INSERT INTO page VALUES(20,1),(21,2);
            INSERT INTO node(id,page_id,x,y,subject,content,symb,record_date) VALUES(10,20,0,0,'기존','유지','NVDA','2026-01-01');
            """);
        db.execute(Files.readString(Path.of("../trader-controller/db/migration/V055__canvas_ai_runs.sql")));
        db.execute(Files.readString(Path.of("../trader-controller/db/migration/V056__canvas_ai_before_state.sql")));
        nodes=mock(NodeService.class);edges=mock(EdgeService.class);
        positions=mock(TeamNodePositionWriter.class);locks=mock(CanvasLockService.class);
        when(locks.tryAcquire(anyLong(),anyLong(),anyLong(),anyLong())).thenReturn(true);
        when(locks.isHeldBy(anyLong(),anyLong(),anyLong(),anyLong())).thenReturn(true);
        when(nodes.updateTeamNode(eq(7L),eq(20L),anyLong(),eq(1L),any())).thenAnswer(call->{
            long id=call.getArgument(2);var dto=(com.example.trader.dto.map.UpdateTeamNodeContentRequest)call.getArgument(4);
            assertThat(dto.getDirtyFields()).isSubsetOf("subject","content");
            assertThat(dto.isNoteIdsSupplied()).isFalse();assertThat(dto.isSymbSupplied()).isFalse();assertThat(dto.isRecordDateSupplied()).isFalse();
            if(dto.isSubjectSupplied()) db.update("UPDATE node SET subject=? WHERE id=?",dto.getSubject(),id);
            if(dto.isContentSupplied()) db.update("UPDATE node SET content=? WHERE id=?",dto.getContent(),id);
            db.update("UPDATE node SET version=version+1,lock_version=lock_version+1 WHERE id=? AND version=?",id,dto.getBaseVersion());
            return ResponseNodeDto.builder().id(id).build();
        });
        when(positions.write(eq(7L),eq(20L),anyLong(),eq(1L),anyInt(),anyDouble(),anyDouble())).thenAnswer(call->{
            long id=call.getArgument(2);int version=call.getArgument(4);
            int count=db.update("UPDATE node SET x=?,y=?,version=version+1,lock_version=lock_version+1 WHERE id=? AND version=?",call.getArgument(5),call.getArgument(6),id,version);
            return count==1?Optional.of(version+1):Optional.empty();
        });
        when(nodes.createTeamNode(any(),eq(7L),eq(20L),anyLong())).thenAnswer(call->{
            var n=(com.example.trader.dto.map.CreateNodeRequest)call.getArgument(0);long id=nodeIds.incrementAndGet();
            db.update("INSERT INTO node(id,page_id,x,y,subject,content,symb,record_date) VALUES(?,20,?,?,?,?,?,?)",
                id,n.getX(),n.getY(),n.getSubject(),n.getContent(),n.getSymb(),n.getRecordDate());
            return ResponseNodeDto.builder().id(id).pageId(20L).build();
        });
        when(edges.createTeamEdge(eq(7L),eq(20L),any())).thenAnswer(call->{
            var e=(com.example.trader.dto.map.RequestEdgeDto)call.getArgument(2);long id=edgeIds.incrementAndGet();
            db.update("INSERT INTO edge VALUES(?,20,?,?,?)",id,e.getSourceId(),e.getTargetId(),e.getLabel());
            return ResponseEdgeDto.builder().id(id).build();
        });
        doAnswer(call->{db.update("DELETE FROM edge WHERE id=?",(Long)call.getArgument(2));return null;}).when(edges).deleteTeamEdge(anyLong(),anyLong(),anyLong());
        doAnswer(call->{db.update("DELETE FROM node WHERE id=?",(Long)call.getArgument(2));return null;}).when(nodes).deleteTeamNode(anyLong(),anyLong(),anyLong());
        service=new CanvasAgentService(db,new ObjectMapper(),nodes,edges,mock(EntityManager.class),positions,locks,mock(NodeCacheService.class),mock(GraphCacheService.class));
    }
    @AfterEach void cleanup(){if(schema!=null)db.execute("DROP SCHEMA "+schema+" CASCADE");}
    String fingerprint(){return (String)service.snapshot(1,7,20).get("fingerprint");}
    CanvasAgentContracts.Claim claim(UUID id,String fp){return new CanvasAgentContracts.Claim(id,"a".repeat(64),fp);}
    CanvasAgentContracts.Apply edit(UUID id,String fp){return new CanvasAgentContracts.Apply(id,fp,
        List.of(new CanvasAgentContracts.NewNode("n1","AI 관점","분석과 근거","NVDA",400,0)),
        List.of(new CanvasAgentContracts.NewEdge("10","n1","검토 관점")));}
    Map<String,Object> apply(CanvasAgentContracts.Apply edit){return tx.execute(s->service.apply(1,7,20,edit));}
    UUID begin(String fp){UUID id=UUID.randomUUID();tx.execute(s->service.claim(1,7,20,claim(id,fp)));return id;}

    @Test void atomicAppendIdempotentReadAndUndoPreserveOriginal() {
        String fp=fingerprint();UUID id=begin(fp);var edit=edit(id,fp);
        assertThat(apply(edit).get("status")).isEqualTo("APPLIED");apply(edit);
        verify(nodes,times(1)).createTeamNode(any(),eq(7L),eq(20L),eq(1L));
        assertThat(db.queryForObject("SELECT count(*) FROM node",Long.class)).isEqualTo(2);
        assertThat(service.status(1,7,20,id).get("status")).isEqualTo("APPLIED");
        assertThat(tx.execute(s->service.undo(1,7,20,id)).get("status")).isEqualTo("UNDONE");
        tx.execute(s->service.undo(1,7,20,id));
        assertThat(fingerprint()).isEqualTo(fp);
        assertThat(db.queryForObject("SELECT content FROM node WHERE id=10",String.class)).isEqualTo("유지");
    }
    @Test void staleSnapshotAndLaterUserEditsCannotBeOverwrittenOrUndone() {
        String fp=fingerprint();UUID id=begin(fp);db.update("UPDATE node SET content='사용자 변경',version=1 WHERE id=10");
        assertThatThrownBy(()->apply(edit(id,fp))).isInstanceOf(ResponseStatusException.class);verifyNoInteractions(nodes,edges);
        tx.execute(s->service.cancel(1,7,20,id));String nextFp=fingerprint();UUID run=begin(nextFp);apply(edit(run,nextFp));
        db.update("UPDATE node SET x=12 WHERE id=10");
        assertThatThrownBy(()->tx.execute(s->service.undo(1,7,20,run))).isInstanceOf(ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM node",Long.class)).isEqualTo(2);
    }
    @Test void cancelAndExpiryFenceLateWrites() {
        String fp=fingerprint();UUID id=begin(fp);tx.execute(s->service.cancel(1,7,20,id));
        assertThatThrownBy(()->apply(edit(id,fp))).isInstanceOf(ResponseStatusException.class);
        UUID expired=begin(fp);db.update("UPDATE canvas_ai_run SET expires_at=now()-interval '1 minute' WHERE id=?",expired);
        assertThat(service.status(1,7,20,expired).get("status")).isEqualTo("EXPIRED");
        assertThatThrownBy(()->apply(edit(expired,fp))).isInstanceOf(ResponseStatusException.class);verifyNoInteractions(nodes,edges);
    }
    @Test void anotherMemberCannotReadCancelOrUndoOwnersRunAndForeignGraphDenied() {
        String fp=fingerprint();UUID id=begin(fp);
        assertThatThrownBy(()->service.status(2,7,20,id)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->tx.execute(s->service.cancel(2,7,20,id))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.snapshot(1,8,21)).isInstanceOf(ResponseStatusException.class);
        db.update("DELETE FROM user_team WHERE user_id=1");
        assertThatThrownBy(()->apply(edit(id,fp))).isInstanceOf(ResponseStatusException.class);verifyNoInteractions(nodes,edges);
    }
    @Test void writeFailureRollsBackAllNodesEdgesAndReceipt() {
        String fp=fingerprint();UUID id=begin(fp);when(edges.createTeamEdge(anyLong(),anyLong(),any())).thenThrow(new IllegalStateException("failure"));
        assertThatThrownBy(()->apply(edit(id,fp))).isInstanceOf(IllegalStateException.class);
        assertThat(db.queryForObject("SELECT count(*) FROM node",Long.class)).isEqualTo(1);
        assertThat(service.status(1,7,20,id).get("status")).isEqualTo("CLAIMED");
    }
    @Test void foreignNodeReferenceAndDuplicateDraftKeyFailBeforeAnyWrite() {
        String fp=fingerprint();UUID id=begin(fp);var edit=edit(id,fp);
        var foreign=new CanvasAgentContracts.Apply(id,fp,edit.nodes(),List.of(new CanvasAgentContracts.NewEdge("999","n1","x")));
        assertThatThrownBy(()->apply(foreign)).isInstanceOf(ResponseStatusException.class);
        var duplicate=new CanvasAgentContracts.Apply(id,fp,List.of(edit.nodes().get(0),edit.nodes().get(0)),List.of());
        assertThatThrownBy(()->apply(duplicate)).isInstanceOf(ResponseStatusException.class);verifyNoInteractions(nodes,edges);
    }
    @Test void onlyOneOwnerCanClaimGraphAcrossServiceInstances() throws Exception {
        String fp=fingerprint();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            List<Future<Boolean>> jobs=new ArrayList<>();
            for(long owner:List.of(1L,2L)) jobs.add(pool.submit(()->{gate.await();try{
                tx.execute(s->service.claim(owner,7,20,claim(UUID.randomUUID(),fp)));return true;
            }catch(ResponseStatusException e){return false;}}));
            gate.countDown();int count=0;for(var job:jobs)if(job.get(10,TimeUnit.SECONDS))count++;
            assertThat(count).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }

    CanvasAgentContracts.Apply revise(UUID id,String fp,CanvasAgentContracts.NodeEdit... patches) {
        return new CanvasAgentContracts.Apply(id,fp,List.of(),List.of(),List.of(patches));
    }
    @Test void existingContentAndPositionAreBackedUpAndRestoredWithIncreasingVersions() {
        String fp=fingerprint();UUID id=begin(fp);
        var patch=new CanvasAgentContracts.NodeEdit(10,"정리한 제목","새 분석",380.0,240.0);
        var request=revise(id,fp,patch);var first=apply(request);apply(request);
        assertThat(db.queryForObject("SELECT content FROM node WHERE id=10",String.class)).isEqualTo("새 분석");
        assertThat(db.queryForObject("SELECT before_state->0->>'content' FROM canvas_ai_run WHERE id=?",String.class,id)).isEqualTo("유지");
        assertThat(first.toString()).doesNotContain("before_state","유지");
        tx.execute(s->service.undo(1,7,20,id));tx.execute(s->service.undo(1,7,20,id));
        var original=db.queryForMap("SELECT subject,content,x,y,symb,record_date,version FROM node WHERE id=10");
        assertThat(original).containsEntry("subject","기존").containsEntry("content","유지").containsEntry("x",0.0).containsEntry("y",0.0).containsEntry("symb","NVDA").containsEntry("version",4);
        assertThat(original.get("record_date").toString()).isEqualTo("2026-01-01");
        verify(locks,times(2)).release(7L,20L,10L,1L);
    }
    @Test void mixedExistingChangesAndNewNodesRollbackTogetherOnEdgeFailure() {
        String fp=fingerprint();UUID id=begin(fp);var append=edit(id,fp);
        var request=new CanvasAgentContracts.Apply(id,fp,append.nodes(),append.edges(),List.of(new CanvasAgentContracts.NodeEdit(10,"changed","edited",99.0,88.0)));
        when(edges.createTeamEdge(anyLong(),anyLong(),any())).thenThrow(new IllegalStateException("edge failure"));
        assertThatThrownBy(()->apply(request)).isInstanceOf(IllegalStateException.class);
        assertThat(fingerprint()).isEqualTo(fp);
        assertThat(db.queryForObject("SELECT before_state IS NULL FROM canvas_ai_run WHERE id=?",Boolean.class,id)).isTrue();
        assertThat(service.status(1,7,20,id).get("status")).isEqualTo("CLAIMED");
        verify(locks).release(7L,20L,10L,1L);
    }
    @Test void invalidExistingReferencesCoordinatesAndDuplicateEditsAreRejectedBeforeWrites() {
        String fp=fingerprint();UUID id=begin(fp);
        for(var patch:List.of(new CanvasAgentContracts.NodeEdit(999,"x",null,null,null),
                new CanvasAgentContracts.NodeEdit(10,null,null,1.0,null),new CanvasAgentContracts.NodeEdit(10,null,null,Double.NaN,0.0))) {
            assertThatThrownBy(()->apply(revise(id,fp,patch))).isInstanceOf(ResponseStatusException.class);
        }
        var patch=new CanvasAgentContracts.NodeEdit(10,"x",null,null,null);
        assertThatThrownBy(()->apply(revise(id,fp,patch,patch))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(nodes,positions,locks);
    }
    @Test void activeMovementLockAndExpiredLeasePreventWritesAndUndo() {
        String fp=fingerprint();UUID id=begin(fp);var request=revise(id,fp,new CanvasAgentContracts.NodeEdit(10,null,null,1.0,2.0));
        when(locks.tryAcquire(7L,20L,10L,1L)).thenReturn(false);
        assertThatThrownBy(()->apply(request)).isInstanceOf(ResponseStatusException.class);verifyNoInteractions(positions);
        when(locks.tryAcquire(7L,20L,10L,1L)).thenReturn(true);when(locks.isHeldBy(7L,20L,10L,1L)).thenReturn(false);
        assertThatThrownBy(()->apply(request)).isInstanceOf(ResponseStatusException.class);assertThat(fingerprint()).isEqualTo(fp);
        when(locks.isHeldBy(7L,20L,10L,1L)).thenReturn(true);apply(request);
        when(locks.tryAcquire(7L,20L,10L,1L)).thenReturn(false);
        assertThatThrownBy(()->tx.execute(s->service.undo(1,7,20,id))).isInstanceOf(ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT x FROM node WHERE id=10",Double.class)).isEqualTo(1.0);
    }
    @Test void laterHumanEditBlocksRestoreAndKeepsPrivateBeforeState() {
        String fp=fingerprint();UUID id=begin(fp);apply(revise(id,fp,new CanvasAgentContracts.NodeEdit(10,null,"AI 변경",null,null)));
        db.update("UPDATE node SET content='사용자 후속 편집',version=version+1 WHERE id=10");
        assertThatThrownBy(()->tx.execute(s->service.undo(1,7,20,id))).isInstanceOf(ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT content FROM node WHERE id=10",String.class)).isEqualTo("사용자 후속 편집");
        assertThat(db.queryForObject("SELECT before_state->0->>'content' FROM canvas_ai_run WHERE id=?",String.class,id)).isEqualTo("유지");
        assertThat(service.status(1,7,20,id).toString()).doesNotContain("유지");
    }
    @Test void longOriginalIsRestoredWithoutTruncationAndLegacyNullIsHeldForContentOnly() {
        String longOriginal="원문".repeat(4000);db.update("UPDATE node SET content=? WHERE id=10",longOriginal);
        String fp=fingerprint();UUID id=begin(fp);apply(revise(id,fp,new CanvasAgentContracts.NodeEdit(10,null,"요약",null,null)));
        tx.execute(s->service.undo(1,7,20,id));assertThat(db.queryForObject("SELECT content FROM node WHERE id=10",String.class)).isEqualTo(longOriginal);
        db.update("UPDATE node SET content=NULL WHERE id=10");fp=fingerprint();UUID nullRun=begin(fp);String finalFp=fp;
        assertThatThrownBy(()->apply(revise(nullRun,finalFp,new CanvasAgentContracts.NodeEdit(10,null,"새 내용",null,null)))).isInstanceOf(ResponseStatusException.class);
        apply(revise(nullRun,fp,new CanvasAgentContracts.NodeEdit(10,null,null,5.0,6.0)));
        tx.execute(s->service.undo(1,7,20,nullRun));assertThat(db.queryForObject("SELECT content IS NULL FROM node WHERE id=10",Boolean.class)).isTrue();
    }
}
