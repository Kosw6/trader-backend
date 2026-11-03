package com.example.trader.ai;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AI_CONTRACT_TEST_DB",matches="local-isolated")
class AiSourcePostgresTest {
    private JdbcTemplate db; private TransactionTemplate tx; private AiSourceReader reader; private String schema;
    @BeforeEach void prepare() throws Exception {
        schema="ai_contract_"+UUID.randomUUID().toString().replace("-","");
        var base=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55439/postgres","ai_contract","");
        new JdbcTemplate(base).execute("create schema " + schema);
        var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55439/postgres?currentSchema="+schema,"ai_contract","");
        db=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds)); reader=new AiSourceReader(db);
        tx.executeWithoutResult(s->{
            try {
                db.execute(Files.readString(Path.of("src/test/resources/ai/source_contract_fixture.sql")));
                db.execute(Files.readString(Path.of("../trader-controller/db/migration/V024__track_ai_search_sources.sql")));
            } catch(Exception e) {throw new RuntimeException(e);}
        });
    }
    @AfterEach void cleanup() {
        if(schema!=null && schema.matches("ai_contract_[a-f0-9]{32}")) db.execute("drop schema "+schema+" cascade");
    }
    long revision(String type,long id) { return db.queryForObject("select revision from search_source_state where source_type=? and source_id=?",Long.class,type,id); }
    @Test void exact_title_uses_current_authorized_rows_and_reports_ambiguity() {
        var personal = new AiScope(false,1L,"PERSONAL",null);
        var team = new AiScope(false,1L,"TEAM",7L);
        assertThat(reader.exactTitle("없는제목",personal).get("items")).isEqualTo(List.of());
        assertThat(reader.exactTitle("다른 노트",personal).get("items")).isEqualTo(List.of());
        assertThat(reader.exactTitle("다른 노트",team).get("items"))
                .isEqualTo(List.of(Map.of("sourceType","NOTE","sourceId","2")));
        assertThat(reader.exactTitle("개인",personal).get("items"))
                .isEqualTo(List.of(Map.of("sourceType","NODE","sourceId","1")));
        db.update("update note set subject='새 이름' where id=1");
        assertThat(reader.exactTitle("새이름",personal).get("items"))
                .isEqualTo(List.of(Map.of("sourceType","NOTE","sourceId","1")));
        assertThat(reader.exactTitle("내 노트",personal).get("items")).isEqualTo(List.of());
        db.update("update node set subject='새 이름' where id=1");
        assertThat(reader.exactTitle("새 이름",personal).get("status")).isEqualTo("AMBIGUOUS");
        db.update("delete from node_note_link where id=1");
        assertThat(reader.exactTitle("다른 노트",team).get("items")).isEqualTo(List.of());
        assertThatThrownBy(()->reader.exactTitle("다른 노트",new AiScope(false,99L,"TEAM",7L)))
                .isInstanceOf(AiApiException.class);
    }
    AiScope index() {return new AiScope(true,null,null,null);}
    @Test void source_state_outbox_are_atomic_and_same_transaction_coalesces() {
        tx.executeWithoutResult(s->{db.update("update note set content='new' where id=1"); db.update("update note set subject='changed' where id=1");});
        assertThat(revision("NOTE",1)).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from search_source_outbox",Integer.class)).isEqualTo(1);
        tx.executeWithoutResult(s->{db.update("update note set content='rollback' where id=1");s.setRollbackOnly();});
        assertThat(revision("NOTE",1)).isEqualTo(2);
        assertThat(db.queryForObject("select content from note where id=1",String.class)).isEqualTo("new");
        assertThat(db.queryForObject("select count(*) from search_source_outbox",Integer.class)).isEqualTo(1);
    }
    @Test void position_style_and_noop_do_not_reindex_but_relationships_do() {
        db.update("update node set x=10,y=20 where id=2"); db.update("update edge set stroke='blue' where id=1");
        db.update("update note set content=content where id=1");
        assertThat(revision("NODE",2)).isEqualTo(1); assertThat(revision("EDGE",1)).isEqualTo(1);
        db.update("delete from edge where id=1");
        assertThat(revision("NODE",2)).isEqualTo(2); assertThat(revision("NODE",3)).isEqualTo(2);
        assertThat(reader.source("EDGE",1,null,index()).get("status")).isEqualTo("DELETED");
    }
    @Test void permissions_precede_revision_and_missing_is_not_deleted() {
        assertThat(reader.source("NOTE",2,null,new AiScope(false,1L,"TEAM",7L)).get("status")).isEqualTo("AVAILABLE");
        assertThatThrownBy(()->reader.source("NOTE",2,null,new AiScope(false,1L,"PERSONAL",null))).isInstanceOf(AiApiException.class);
        db.update("delete from node_note_link where id=1");
        assertThatThrownBy(()->reader.source("NOTE",2,null,new AiScope(false,1L,"TEAM",7L))).isInstanceOf(AiApiException.class);
        assertThat(reader.source("NOTE",2,1L,index()).get("status")).isEqualTo("REVISION_MISMATCH");
        assertThat(reader.source("NOTE",2,1L,index())).doesNotContainKey("document");
        assertThatThrownBy(()->reader.source("NOTE",999,null,index())).satisfies(e->assertThat(((AiApiException)e).code).isEqualTo("SOURCE_NOT_FOUND"));
        db.update("delete from search_source_state where source_type='NOTE' and source_id=1");
        assertThatThrownBy(()->reader.source("NOTE",1,null,index())).satisfies(e->assertThat(((AiApiException)e).code).isEqualTo("SOURCE_STATE_INCONSISTENT"));
    }
    @Test void cascade_preserves_tombstones_and_remaining_scope_changes() {
        db.update("delete from node where id=2");
        assertThat(reader.source("NODE",2,null,index()).get("status")).isEqualTo("DELETED");
        assertThat(reader.source("EDGE",1,null,index()).get("status")).isEqualTo("DELETED");
        assertThat(revision("NOTE",2)).isEqualTo(2); assertThat(revision("NODE",3)).isEqualTo(2);
        assertThat(reader.source("NOTE",2,null,index()).get("scopeKeys")).isEqualTo(List.of("PERSONAL:2"));
    }
    @Test void container_scope_changes_propagate_and_invalid_writes_fail() {
        db.update("update directory set team_id=8 where id=2");
        assertThat(revision("NODE",2)).isEqualTo(2); assertThat(revision("EDGE",1)).isEqualTo(2); assertThat(revision("NOTE",2)).isEqualTo(2);
        assertThatThrownBy(()->db.update("update note set note_date=null where id=1")).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void concurrent_revision_increment_is_not_lost() throws Exception {
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            Callable<Void> actual=()->{gate.await();tx.executeWithoutResult(s->db.execute("select ai_search_touch('NOTE',1,ARRAY['RELATION'])"));return null;};
            var a=pool.submit(actual); var b=pool.submit(actual); gate.countDown(); a.get(10,TimeUnit.SECONDS);b.get(10,TimeUnit.SECONDS);
            assertThat(revision("NOTE",1)).isEqualTo(3);
        } finally {pool.shutdownNow();}
    }
    @Test void repeatable_read_keeps_original_and_revision_in_same_snapshot() throws Exception {
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(s->{
            var before=reader.source("NOTE",1,null,index());
            var pool=Executors.newSingleThreadExecutor();
            try {pool.submit(()->db.update("update note set content='concurrent' where id=1")).get(5,TimeUnit.SECONDS);}
            catch(Exception e){throw new RuntimeException(e);} finally {pool.shutdownNow();}
            assertThat(reader.source("NOTE",1,1L,index())).isEqualTo(before);
        });
        assertThat(reader.source("NOTE",1,1L,index()).get("status")).isEqualTo("REVISION_MISMATCH");
    }
    @Test void outbox_failure_rolls_back_original_and_revision() {
        db.execute("alter table search_source_outbox add constraint forced_failure check(false) not valid");
        assertThatThrownBy(()->tx.executeWithoutResult(s->db.update("update note set content='must rollback' where id=1")))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(revision("NOTE",1)).isEqualTo(1);
        assertThat(db.queryForObject("select content from note where id=1",String.class)).isEqualTo("본문");
    }
    @Test void relation_paging_is_numeric_signed_and_revision_bound() {
        db.update("insert into note values(10,2,'추가','',null,'2026-10-02')");
        db.update("insert into node_note_link values(10,2,10)");
        long revision=revision("NODE",2);
        var scope=new AiScope(false,1L,"TEAM",7L);
        var codec=new AiCursorCodec("test",Map.of("test",new byte[32]),300,java.time.Clock.systemUTC());
        var first=reader.relations(2,revision,"LINKED_NOTES",scope,1,null,codec);
        assertThat(first.get("items")).isEqualTo(List.of(Map.of("noteId","2")));
        var second=reader.relations(2,revision,"LINKED_NOTES",scope,1,(String)first.get("nextCursor"),codec);
        assertThat(second.get("items")).isEqualTo(List.of(Map.of("noteId","10")));
        assertThat(first.get("relationsHash")).isEqualTo(second.get("relationsHash"));
        assertThatThrownBy(()->reader.relations(2,revision,"LINKED_NOTES",new AiScope(false,2L,"TEAM",7L),1,(String)first.get("nextCursor"),codec)).isInstanceOf(AiApiException.class);
        db.update("delete from node_note_link where id=10");
        var stale=reader.relations(2,revision,"LINKED_NOTES",scope,1,(String)first.get("nextCursor"),codec);
        assertThat(stale.get("status")).isEqualTo("REVISION_MISMATCH");assertThat(stale).doesNotContainKey("items");
    }
    @Test void evidence_checks_preserve_meaning_and_hide_inaccessible_metadata() {
        var scope=new AiScope(false,1L,"TEAM",7L);
        var original=reader.source("NOTE",2,null,scope);
        var context=new AiRequestContext(false,1L,"test",java.time.Instant.now(),java.time.Instant.now().plusSeconds(120));
        var entry=new LinkedHashMap<String,Object>();
        entry.put("evidenceId","e1");entry.put("usage","DOCUMENT");entry.put("sourceType","NOTE");entry.put("sourceId","2");
        entry.put("observedRevision","1");entry.put("scope",Map.of("type","TEAM","teamId","7"));
        entry.put("hashPolicyVersion","v1");entry.put("documentHash",original.get("documentHash"));
        var checks=AiEvidenceCheck.parse(new String(AiCanonicalJson.bytes(List.of(entry)),java.nio.charset.StandardCharsets.UTF_8),context);
        assertThat(reader.evidence(checks).toString()).contains("contentChanged=false");
        db.update("update note set content='새 내용' where id=2");
        assertThat(reader.evidence(checks).toString()).contains("contentChanged=true");
        db.update("delete from user_team where user_id=1");
        assertThat(reader.evidence(checks)).isEqualTo(Map.of("results",List.of(Map.of("evidenceId","e1","status","UNAVAILABLE"))));
        entry.put("unexpected",true);
        assertThatThrownBy(()->AiEvidenceCheck.parse(new String(AiCanonicalJson.bytes(List.of(entry)),java.nio.charset.StandardCharsets.UTF_8),context)).isInstanceOf(AiApiException.class);
    }
    @Test void http_contract_uses_service_boundary_and_structured_errors() throws Exception {
        var clock=java.time.Clock.systemUTC();var now=clock.instant();
        var codec=new AiCursorCodec("test",Map.of("test",new byte[32]),300,clock);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new AiSourceController(reader,codec,new AiReadLimits(1048576,30,100,10000)))
                .setControllerAdvice(new AiExceptionHandler())
                .addFilters(new AiServiceAuthFilter(true,"r".repeat(43),"i".repeat(43),120,5,clock)).build();
        var get=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/internal/ai/retrieval/sources/NOTE/2")
                .header("Authorization","Bearer "+"r".repeat(43)).header("X-AI-User-Id","1")
                .header("X-AI-Accepted-At",now.toString()).header("X-AI-Deadline-At",now.plusSeconds(120).toString())
                .param("scopeType","TEAM").param("teamId","7");
        mvc.perform(get).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.sourceRevision").value("1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.document.title").value("다른 노트"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/internal/ai/indexing/sources/NOTE/2")
                .header("Authorization","Bearer "+"i".repeat(43)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
}
