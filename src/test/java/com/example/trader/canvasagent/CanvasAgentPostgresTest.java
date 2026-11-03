package com.example.trader.canvasagent;

import com.example.trader.dto.map.ResponseEdgeDto;
import com.example.trader.dto.map.ResponseNodeDto;
import com.example.trader.service.EdgeService;
import com.example.trader.service.NodeService;
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
        nodes=mock(NodeService.class);edges=mock(EdgeService.class);
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
        service=new CanvasAgentService(db,new ObjectMapper(),nodes,edges,mock(EntityManager.class));
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
}
