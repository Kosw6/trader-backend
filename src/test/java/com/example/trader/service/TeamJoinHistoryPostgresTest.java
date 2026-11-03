package com.example.trader.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AI_CONTRACT_TEST_DB",matches="local-isolated")
class TeamJoinHistoryPostgresTest {
    JdbcTemplate db;String schema;TransactionTemplate tx;
    @BeforeEach void setup() throws Exception {
        schema="join_history_"+UUID.randomUUID().toString().replace("-","");
        var root=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55439/postgres","ai_contract",""));
        root.execute("create schema "+schema);
        var ds=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:55439/postgres?currentSchema="+schema,"ai_contract","");
        db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        db.execute("create table team(id bigint primary key)");
        db.execute("create table join_request(id bigserial primary key,team_id bigint references team(id),requester_id bigint,status text, constraint uq_join_request_team_requester unique(team_id,requester_id))");
        db.execute("insert into team values(1),(2)");
        db.execute("insert into join_request(team_id,requester_id,status) values(1,10,'APPROVED'),(2,10,'REJECTED')");
        db.execute(Files.readString(Path.of("../trader-controller/db/migration/V049__team_join_request_history.sql")));
    }
    @AfterEach void cleanup() {if(db!=null) db.execute("drop schema "+schema+" cascade");}
    @Test void decisions_survive_and_new_request_has_different_id() {
        long old=db.queryForObject("select id from join_request where team_id=1",Long.class);
        long fresh=db.queryForObject("insert into join_request(team_id,requester_id,status) values(1,10,'PENDING') returning id",Long.class);
        assertThat(fresh).isNotEqualTo(old);
        assertThat(db.queryForObject("select status from join_request where id=?",String.class,old)).isEqualTo("APPROVED");
        assertThatThrownBy(()->db.update("insert into join_request(team_id,requester_id,status) values(1,10,'PENDING')"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        db.update("update join_request set status='REJECTED' where id=?",fresh);
        db.update("insert into join_request(team_id,requester_id,status) values(1,10,'PENDING')");
        assertThat(db.queryForObject("select count(*) from join_request where team_id=1",Integer.class)).isEqualTo(3);
    }
    @Test void request_cleanup_and_parent_deletion_rollback_together() {
        assertThatThrownBy(()->tx.executeWithoutResult(s->{
            db.update("delete from join_request where team_id=1");
            db.update("delete from team where id=1");throw new IllegalStateException("synthetic failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(db.queryForObject("select count(*) from join_request where team_id=1",Integer.class)).isEqualTo(1);
        tx.executeWithoutResult(s->{db.update("delete from join_request where team_id=1");db.update("delete from team where id=1");});
        assertThat(db.queryForObject("select count(*) from team where id=1",Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from join_request where team_id=2",Integer.class)).isEqualTo(1);
    }
    @Test void concurrent_reapplication_creates_only_one_pending_request() throws Exception {
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Integer> join=()->{
            assertThat(start.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return tx.execute(s->{
                db.queryForObject("select id from team where id=1 for update",Long.class);
                int pending=db.queryForObject("select count(*) from join_request where team_id=1 and requester_id=10 and status='PENDING'",Integer.class);
                return pending==0 ? db.update("insert into join_request(team_id,requester_id,status) values(1,10,'PENDING')") : 0;
            });
        };
        try {
            var first=pool.submit(join);var second=pool.submit(join);start.countDown();
            assertThat(first.get(10,java.util.concurrent.TimeUnit.SECONDS)+second.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(db.queryForObject("select count(*) from join_request where team_id=1",Integer.class)).isEqualTo(2);
        } finally {pool.shutdownNow();}
    }
}
