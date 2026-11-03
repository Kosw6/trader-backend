package com.example.trader.service;

import com.example.trader.entity.*;
import com.example.trader.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="AI_CONTRACT_TEST_DB",matches="local-isolated")
class PrivateCanvasSnapshotPostgresTest {
    JdbcTemplate db; TransactionTemplate tx; PrivateCanvasSnapshotService service; String schema;
    NodeRepository nodes; EdgeRepository edges; PageRepository pages; DirectoryRepository directories;
    @BeforeEach void setup() throws Exception {
        schema="snapshot_contract_"+UUID.randomUUID().toString().replace("-","");
        var base=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55439/postgres","ai_contract","");
        new JdbcTemplate(base).execute("CREATE SCHEMA "+schema);
        var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55439/postgres?currentSchema="+schema,"ai_contract","");
        db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("""
            CREATE TABLE users(id bigint PRIMARY KEY);
            CREATE TABLE team(id bigint PRIMARY KEY,team_name text);
            CREATE TABLE user_team(user_id bigint,team_id bigint REFERENCES team(id) ON DELETE CASCADE);
            CREATE TABLE directory(id bigint PRIMARY KEY,team_id bigint REFERENCES team(id) ON DELETE CASCADE);
            CREATE TABLE page(id bigint PRIMARY KEY,title text,directory_id bigint REFERENCES directory(id) ON DELETE CASCADE);
            CREATE TABLE node(id bigint PRIMARY KEY,page_id bigint REFERENCES page(id) ON DELETE CASCADE,x float8,y float8,subject text,content text,symb text,record_date date);
            CREATE TABLE edge(id bigint PRIMARY KEY,page_id bigint REFERENCES page(id) ON DELETE CASCADE,source_id bigint,target_id bigint,type text,label text,source_handle text,target_handle text,variant text,animated boolean,stroke text,stroke_width integer);
            INSERT INTO users VALUES(1),(2); INSERT INTO team VALUES(7,'그룹 하나'),(8,'그룹 둘');
            INSERT INTO user_team VALUES(1,7),(2,7),(1,8);
            INSERT INTO directory VALUES(10,7),(11,8); INSERT INTO page VALUES(20,'그래프',10),(21,'두번째',11);
            INSERT INTO node VALUES(100,20,10,20,'근거','저장 본문','NVDA','2026-10-22'),(101,20,100,30,'결론','내용',null,null);
            INSERT INTO edge VALUES(200,20,100,101,'default','연결','rightS','leftT',null,false,'#8899aa',2);
            """);
        db.execute(Files.readString(Path.of("../trader-controller/db/migration/V048__private_canvas_snapshots.sql")));
        var users=mock(UserRepository.class);nodes=mock(NodeRepository.class);edges=mock(EdgeRepository.class);
        directories=mock(DirectoryRepository.class);pages=mock(PageRepository.class);
        var owner=mock(User.class);
        when(owner.getId()).thenReturn(1L);
        when(users.getReferenceById(1L)).thenReturn(owner);
        when(directories.save(any())).thenAnswer(a->{Directory d=a.getArgument(0);ReflectionTestUtils.setField(d,"id",900L);return d;});
        when(pages.save(any())).thenAnswer(a->{Page p=a.getArgument(0);ReflectionTestUtils.setField(p,"id",901L);return p;});
        AtomicLong ids=new AtomicLong(1000);
        when(nodes.save(any())).thenAnswer(a->{Node n=a.getArgument(0);ReflectionTestUtils.setField(n,"id",ids.incrementAndGet());return n;});
        service=new PrivateCanvasSnapshotService(db,new ObjectMapper(),users,directories,pages,nodes,edges);
    }
    @AfterEach void cleanup(){if(schema!=null)db.execute("DROP SCHEMA "+schema+" CASCADE");}
    PrivateCanvasSnapshotService.Detail save(long user,long team,long page,UUID id,UUID replace){
        return tx.execute(s->service.save(user,new PrivateCanvasSnapshotService.SaveRequest(id,team,page,"보관",replace)));
    }
    @Test void fivePerUserPerGroupNotPerPage() {
        for(int i=0;i<5;i++)save(1,7,20,UUID.randomUUID(),null);
        assertThatThrownBy(()->save(1,7,20,UUID.randomUUID(),null)).isInstanceOf(ResponseStatusException.class);
        save(2,7,20,UUID.randomUUID(),null);save(1,8,21,UUID.randomUUID(),null);
        assertThat(service.list(1,7L)).hasSize(5);assertThat(service.list(1,null)).hasSize(6);
    }
    @Test void privateAndSurvivesGroupAndOriginalDeletion() {
        UUID id=UUID.randomUUID();save(1,7,20,id,null);
        assertThatThrownBy(()->service.get(2,id)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->tx.executeWithoutResult(s->service.delete(2,id))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->save(2,7,20,UUID.randomUUID(),id)).isInstanceOf(ResponseStatusException.class);
        db.update("DELETE FROM team WHERE id=7");
        var kept=service.get(1,id);
        assertThat(kept.graph().path("nodes").get(0).path("content").asText()).isEqualTo("저장 본문");
        assertThat(service.list(1,7L)).hasSize(1);
    }
    @Test void idempotencyAndExplicitReplacement() {
        UUID id=UUID.randomUUID();save(1,7,20,id,null);
        db.update("UPDATE node SET content='new'");save(1,7,20,id,null);
        assertThat(service.get(1,id).graph().path("nodes").get(0).path("content").asText()).isEqualTo("저장 본문");
        UUID replacement=UUID.randomUUID();save(1,7,20,replacement,id);save(1,7,20,replacement,id);
        assertThat(service.list(1,7L)).hasSize(1);
        assertThatThrownBy(()->service.get(1,id)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void nonmemberCannotCaptureOrCopyAnotherOwnersSnapshot() {
        UUID id=UUID.randomUUID();save(1,7,20,id,null);
        assertThatThrownBy(()->save(2,8,21,UUID.randomUUID(),null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->tx.execute(s->service.copy(2,id,new PrivateCanvasSnapshotService.CopyRequest(UUID.randomUUID())))).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(nodes,edges);
    }
    @Test void concurrentRequestsAcrossInstancesCannotExceedFive() throws Exception {
        var pool=Executors.newFixedThreadPool(6);var gate=new CountDownLatch(1);
        try {
            List<Future<Boolean>> jobs=new ArrayList<>();
            for(int i=0;i<6;i++)jobs.add(pool.submit(()->{gate.await();try{save(1,7,20,UUID.randomUUID(),null);return true;}catch(ResponseStatusException e){return false;}}));
            gate.countDown();int count=0;for(var f:jobs)if(f.get(20,TimeUnit.SECONDS))count++;
            assertThat(count).isEqualTo(5);assertThat(service.list(1,7L)).hasSize(5);
        }finally{pool.shutdownNow();}
    }
    @Test void copyRemapsIdsDoesNotReattachNotesAndRetryCreatesNoMoreEntities() {
        UUID id=UUID.randomUUID(),requestId=UUID.randomUUID();save(1,7,20,id,null);
        var request=new PrivateCanvasSnapshotService.CopyRequest(requestId);
        var result=tx.execute(s->service.copy(1,id,request));tx.execute(s->service.copy(1,id,request));
        assertThat(result.pageId()).isEqualTo(901L);verify(pages,times(1)).save(any());verify(nodes,times(2)).save(any());
        var edge=org.mockito.ArgumentCaptor.forClass(Edge.class);verify(edges).save(edge.capture());
        assertThat(edge.getValue().getSource().getId()).isEqualTo(1001L);
        assertThat(edge.getValue().getTarget().getId()).isEqualTo(1002L);
        assertThat(edge.getValue().getSource().getNoteLinks()).isEmpty();
    }
}
