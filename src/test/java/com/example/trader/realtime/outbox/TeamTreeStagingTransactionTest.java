package com.example.trader.realtime.outbox;

import com.example.trader.repository.RealtimeOutboxRepository;
import com.example.trader.realtime.message.RealtimeSubType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({TeamTreeChangeStager.class, TransactionalReliableEventService.class,
        RealtimeOutboxService.class, TeamTreeStagingTransactionTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TeamTreeStagingTransactionTest {
    @Autowired TeamTreeChangeStager stager;
    @Autowired RealtimeOutboxRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CommitProbe probe;

    @Test void rejectsCallsWithoutMutationTransaction() {
        long before = repository.count();
        assertThatThrownBy(() -> stager.stage(1L)).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test void rollbackPersistsNothingAndCommitNotifiesWithTeamScopeOnly() {
        var transaction = new TransactionTemplate(transactionManager);
        long before = repository.count();
        int notifications = probe.events.size();
        transaction.executeWithoutResult(status -> {
            stager.stage(1L);
            repository.flush();
            assertThat(repository.count()).isEqualTo(before + 1);
            assertThat(probe.events).hasSize(notifications);
            status.setRollbackOnly();
        });
        assertThat(repository.count()).isEqualTo(before);
        assertThat(probe.events).hasSize(notifications);
        transaction.executeWithoutResult(status -> stager.stage(1L));
        assertThat(repository.count()).isEqualTo(before + 1);
        assertThat(probe.events).hasSize(notifications + 1);
        var event = probe.events.get(notifications).envelope();
        assertThat(event.getGraphId()).isNull();
        assertThat(event.getTeamId()).isEqualTo(1L);
        assertThat(event.getSubType()).isEqualTo(RealtimeSubType.TEAM_TREE_CHANGED);
        assertThat(event.getEventId()).isNotBlank();
    }

    static class CommitProbe {
        final List<ReliableOutboxCommittedEvent> events = new ArrayList<>();
        @TransactionalEventListener public void committed(ReliableOutboxCommittedEvent event) { events.add(event); }
    }
    @TestConfiguration static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean CommitProbe commitProbe() { return new CommitProbe(); }
    }
}
