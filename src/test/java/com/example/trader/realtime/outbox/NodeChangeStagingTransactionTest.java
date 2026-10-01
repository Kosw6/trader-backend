package com.example.trader.realtime.outbox;

import com.example.trader.repository.RealtimeOutboxRepository;
import com.example.trader.service.event.NodeChange;
import com.example.trader.service.event.NodeChangeEventStager;
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
@Import({OutboxNodeChangeEventStager.class, TransactionalReliableEventService.class,
        RealtimeOutboxService.class, NodeChangeStagingTransactionTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NodeChangeStagingTransactionTest {
    @Autowired NodeChangeEventStager stager;
    @Autowired RealtimeOutboxRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CommitProbe probe;

    @Test
    void rejects_call_without_transaction_before_recording() {
        long before = repository.count();
        assertThatThrownBy(() -> stager.stage(NodeChange.deleted(1L, 2L, 3L)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void rolled_back_record_is_absent_and_only_committed_record_notifies() {
        var transaction = new TransactionTemplate(transactionManager);
        long before = repository.count();
        int notifications = probe.events.size();
        transaction.executeWithoutResult(status -> {
            stager.stage(NodeChange.deleted(1L, 2L, 3L));
            repository.flush();
            assertThat(repository.count()).isEqualTo(before + 1);
            assertThat(probe.events).hasSize(notifications);
            status.setRollbackOnly();
        });
        assertThat(repository.count()).isEqualTo(before);
        assertThat(probe.events).hasSize(notifications);

        transaction.executeWithoutResult(status -> stager.stage(NodeChange.deleted(1L, 2L, 3L)));
        assertThat(repository.count()).isEqualTo(before + 1);
        assertThat(probe.events).hasSize(notifications + 1);
    }

    static class CommitProbe {
        final List<ReliableOutboxCommittedEvent> events = new ArrayList<>();
        @TransactionalEventListener
        public void committed(ReliableOutboxCommittedEvent event) { events.add(event); }
    }

    @TestConfiguration
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean CommitProbe commitProbe() { return new CommitProbe(); }
    }
}
