package com.example.trader.noteupload;

import com.example.trader.entity.*;
import com.example.trader.repository.*;
import com.example.trader.service.NoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="AI_CONTRACT_TEST_DB",matches="local-isolated")
class NoteUploadPostgresTest {
    JdbcTemplate db;TransactionTemplate tx;String schema;NoteUploadService service;
    NoteService notes;NoteRepository repository;ObjectMapper mapper;
    @BeforeEach void setup() throws Exception {
        schema="upload_contract_"+UUID.randomUUID().toString().replace("-","");
        var base=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55439/postgres","ai_contract","");
        new JdbcTemplate(base).execute("CREATE SCHEMA "+schema);
        var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55439/postgres?currentSchema="+schema,"ai_contract","");
        db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("""
            CREATE TABLE users(id bigint PRIMARY KEY);CREATE TABLE team(id bigint PRIMARY KEY);
            CREATE TABLE user_team(id bigint PRIMARY KEY,user_id bigint,team_id bigint);
            CREATE TABLE note(id bigserial PRIMARY KEY,user_id bigint,team_id bigint,subject text,content text,note_date date,stock_symb text,tags jsonb);
            INSERT INTO users VALUES(1),(2);INSERT INTO team VALUES(7);INSERT INTO user_team VALUES(1,1,7);
            """);
        db.execute(Files.readString(Path.of("../trader-controller/db/migration/V050__bounded_note_uploads.sql")));
        mapper=new ObjectMapper().findAndRegisterModules();notes=mock(NoteService.class);repository=mock(NoteRepository.class);
        var users=mock(UserRepository.class);var user=mock(User.class);when(user.getId()).thenReturn(1L);when(users.getReferenceById(1L)).thenReturn(user);
        when(repository.findByIdAndUserId(anyLong(),anyLong())).thenAnswer(a->{
            List<Note> rows=db.query("SELECT * FROM note WHERE id=? AND user_id=?",(rs,n)->{
                try{return Note.builder().id(rs.getLong("id")).user(user).teamId(rs.getObject("team_id",Long.class))
                    .subject(rs.getString("subject")).content(rs.getString("content")).noteDate(rs.getDate("note_date").toLocalDate())
                    .stockSymb(rs.getString("stock_symb")).stockSymbs(new ArrayList<>(Arrays.asList(mapper.readValue(rs.getString("tags"),String[].class)))).build();}
                catch(Exception e){throw new IllegalStateException(e);}
            },a.getArgument(0),a.getArgument(1));return rows.stream().findFirst();
        });
        when(notes.saveNote(any())).thenAnswer(a->{
            Note n=a.getArgument(0);String tags=mapper.writeValueAsString(n.getStockSymbs());
            if(n.getId()==null)return db.queryForObject("INSERT INTO note(user_id,team_id,subject,content,note_date,stock_symb,tags) VALUES(?,?,?,?,?,?,?::jsonb) RETURNING id",Long.class,
                1L,n.getTeamId(),n.getSubject(),n.getContent(),n.getNoteDate(),n.getStockSymb(),tags);
            db.update("UPDATE note SET subject=?,content=?,stock_symb=?,tags=?::jsonb WHERE id=?",n.getSubject(),n.getContent(),n.getStockSymb(),tags,n.getId());return n.getId();
        });
        service=new NoteUploadService(db,mapper,Validation.buildDefaultValidatorFactory().getValidator(),notes,repository,users);
    }
    @AfterEach void drop() {if(db!=null) db.execute("DROP SCHEMA "+schema+" CASCADE");}
    NoteUploadService.Start request(UUID id,String body,Long target,String revision,Long team,int parts) {
        return new NoteUploadService.Start(id,target,revision,"합성 노트",LocalDate.parse("2026-10-29"),team,"NVDA",List.of("NVDA","AAPL"),
            NoteUploadPolicy.utf8(body).length,parts,NoteUploadPolicy.sha256(body));
    }
    UUID staged(String body,Long target,String revision,Long team) {
        UUID id=UUID.randomUUID();List<String> parts=new ArrayList<>();StringBuilder b=new StringBuilder();int bytes=0;
        for(String c:body.codePoints().mapToObj(cp->new String(Character.toChars(cp))).toList()) {
            int size=NoteUploadPolicy.utf8(c).length;if(bytes+size>4096){parts.add(b.toString());b.setLength(0);bytes=0;}b.append(c);bytes+=size;
        }parts.add(b.toString());
        tx.executeWithoutResult(s->service.start(1,request(id,body,target,revision,team,parts.size())));
        for(int i=0;i<parts.size();i++){int n=i;tx.executeWithoutResult(s->service.part(1,id,n,parts.get(n)));}return id;
    }
    Long complete(UUID id) {return tx.execute(s->service.complete(1,id));}
    long count(String table) {return db.queryForObject("SELECT count(*) FROM "+table,Long.class);}
    Note load(Long id) {return repository.findByIdAndUserId(id,1L).orElseThrow();}

    @Test void exact48KiBCreateAndRetrySaveOnceWithNoStagedContentLeft() {
        String body="가".repeat(16384);UUID id=staged(body,null,null,null);
        assertThat(count("note")).isZero();Long note=complete(id);assertThat(complete(id)).isEqualTo(note);
        assertThat(load(note).getContent()).isEqualTo(body);assertThat(load(note).getStockSymbs()).containsExactly("NVDA","AAPL");
        assertThat(count("note_upload_part")).isZero();verify(notes,times(1)).saveNote(any());
        assertThat(service.status(1,id).status()).isEqualTo("COMPLETED");
    }
    @Test void editsAreInvisibleUntilCompletionAndUseTheExistingNoteId() {
        Long note=complete(staged("기존",null,null,null));String revision=NoteUploadPolicy.revision(load(note));
        UUID edit=staged("새 내용😀\n".repeat(1000),note,revision,null);assertThat(load(note).getContent()).isEqualTo("기존");
        assertThat(complete(edit)).isEqualTo(note);assertThat(count("note")).isEqualTo(1);
        assertThat(load(note).getContent()).isEqualTo("새 내용😀\n".repeat(1000));
    }
    @Test void staleEditsAndDeletedTargetsCannotBeTurnedIntoCreates() {
        Long note=complete(staged("기존",null,null,null));UUID edit=staged("수정",note,NoteUploadPolicy.revision(load(note)),null);
        db.update("UPDATE note SET content='다른 편집' WHERE id=?",note);
        assertThatThrownBy(()->complete(edit)).isInstanceOf(NoteUploadException.class);assertThat(load(note).getContent()).isEqualTo("다른 편집");
        db.update("DELETE FROM note WHERE id=?",note);assertThatThrownBy(()->complete(edit)).isInstanceOf(NoteUploadException.class);
        assertThat(count("note")).isZero();
    }
    @Test void otherOwnerCannotReadAppendFinishOrCancel() {
        UUID id=staged("내용",null,null,null);
        assertThatThrownBy(()->service.status(2,id)).isInstanceOf(NoteUploadException.class);
        assertThatThrownBy(()->tx.execute(s->service.part(2,id,0,"내용"))).isInstanceOf(NoteUploadException.class);
        assertThatThrownBy(()->tx.execute(s->service.complete(2,id))).isInstanceOf(NoteUploadException.class);
        assertThatThrownBy(()->tx.executeWithoutResult(s->service.cancel(2,id))).isInstanceOf(NoteUploadException.class);
        assertThat(count("note")).isZero();
    }
    @Test void duplicatePartsAreIdempotentButDifferentPayloadAndMissingPartsFail() {
        UUID id=UUID.randomUUID();var request=request(id,"abcd",null,null,null,2);
        tx.execute(s->service.start(1,request));tx.execute(s->service.start(1,request));
        tx.execute(s->service.part(1,id,0,"ab"));tx.execute(s->service.part(1,id,0,"ab"));
        assertThat(count("note_upload_part")).isEqualTo(1);assertThatThrownBy(()->complete(id)).isInstanceOf(NoteUploadException.class);
        assertThatThrownBy(()->tx.execute(s->service.part(1,id,0,"cd"))).isInstanceOf(NoteUploadException.class);
        tx.execute(s->service.part(1,id,1,"cd"));complete(id);assertThat(count("note")).isEqualTo(1);
    }
    @Test void mismatchedHashAndActiveMarkupAcrossPartBoundariesNeverSave() {
        UUID bad=UUID.randomUUID();tx.execute(s->service.start(1,request(bad,"abcd",null,null,null,1)));
        tx.execute(s->service.part(1,bad,0,"xxxx"));assertThatThrownBy(()->complete(bad)).isInstanceOf(NoteUploadException.class);
        UUID attack=UUID.randomUUID();String a="<scr",b="ipt>alert(1)</script>";
        tx.execute(s->service.start(1,request(attack,a+b,null,null,null,2)));
        tx.execute(s->service.part(1,attack,0,a));tx.execute(s->service.part(1,attack,1,b));
        assertThatThrownBy(()->complete(attack)).isInstanceOf(NoteUploadException.class);verifyNoInteractions(notes);
    }
    @Test void failedReceiptRollsBackTheNoteAndRetainsAllPartsForRetry() {
        UUID id=staged("내용",null,null,null);
        db.execute("CREATE FUNCTION reject_receipt() RETURNS trigger LANGUAGE plpgsql AS $$BEGIN RAISE EXCEPTION 'synthetic receipt failure'; END$$");
        db.execute("CREATE TRIGGER reject_receipt BEFORE UPDATE ON note_upload FOR EACH ROW EXECUTE FUNCTION reject_receipt()");
        assertThatThrownBy(()->complete(id)).isInstanceOf(Exception.class);
        assertThat(count("note")).isZero();assertThat(count("note_upload_part")).isEqualTo(1);assertThat(service.status(1,id).status()).isEqualTo("OPEN");
        db.execute("DROP TRIGGER reject_receipt ON note_upload");complete(id);assertThat(count("note")).isEqualTo(1);
    }
    @Test void concurrentCompletionReturnsOneNote() throws Exception {
        UUID id=staged("내용",null,null,null);var pool=Executors.newFixedThreadPool(6);var gate=new CountDownLatch(1);
        try{List<Future<Long>> jobs=new ArrayList<>();for(int i=0;i<6;i++)jobs.add(pool.submit(()->{gate.await();return complete(id);}));
            gate.countDown();Set<Long> ids=new HashSet<>();for(var job:jobs)ids.add(job.get(20,TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);assertThat(count("note")).isEqualTo(1);verify(notes,times(1)).saveNote(any());
        }finally{pool.shutdownNow();}
    }
    @Test void teamMembershipIsRecheckedAtCommitAndCancellationDoesNotChangeNotes() {
        UUID id=staged("내용",null,null,7L);db.update("DELETE FROM user_team WHERE user_id=1");
        assertThatThrownBy(()->complete(id)).isInstanceOf(NoteUploadException.class);assertThat(count("note")).isZero();
        tx.executeWithoutResult(s->service.cancel(1,id));assertThat(count("note_upload_part")).isZero();
        assertThatThrownBy(()->complete(id)).isInstanceOf(NoteUploadException.class);
    }
    @Test void expiryRemovesRawPartsAndAccountDeletionCascadesReceipts() {
        UUID id=staged("내용",null,null,null);db.update("UPDATE note_upload SET expires_at=clock_timestamp()-interval '1 second' WHERE id=?",id);
        assertThatThrownBy(()->complete(id)).isInstanceOf(NoteUploadException.class);tx.executeWithoutResult(s->service.cleanup());
        assertThat(count("note_upload_part")).isZero();db.update("DELETE FROM users WHERE id=1");assertThat(count("note_upload")).isZero();
    }
    @Test void concurrentStartsNeverExceedThreeOpenSessions() throws Exception {
        var pool=Executors.newFixedThreadPool(6);var gate=new CountDownLatch(1);
        try{List<Future<Boolean>> jobs=new ArrayList<>();for(int i=0;i<6;i++)jobs.add(pool.submit(()->{gate.await();try{
            tx.execute(s->service.start(1,request(UUID.randomUUID(),"x",null,null,null,1)));return true;
        }catch(NoteUploadException e){return false;}}));gate.countDown();int accepted=0;for(var job:jobs)if(job.get(20,TimeUnit.SECONDS))accepted++;
            assertThat(accepted).isEqualTo(3);assertThat(count("note_upload")).isEqualTo(3);
        }finally{pool.shutdownNow();}
    }
    @Test void idempotencyKeyCannotChangeContentOrOwner() {
        UUID id=staged("내용",null,null,null);
        assertThatThrownBy(()->tx.execute(s->service.start(1,request(id,"다름",null,null,null,1)))).isInstanceOf(NoteUploadException.class);
        assertThatThrownBy(()->tx.execute(s->service.start(2,request(id,"내용",null,null,null,1)))).isInstanceOf(NoteUploadException.class);
        assertThat(count("note_upload")).isEqualTo(1);
    }
}
