package com.example.trader.service;

import com.example.trader.dto.canvas.ConflictResult;
import com.example.trader.dto.map.UpdateTeamNodeContentRequest;
import com.example.trader.entity.Directory;
import com.example.trader.entity.Node;
import com.example.trader.entity.Page;
import com.example.trader.entity.Role;
import com.example.trader.entity.Team;
import com.example.trader.entity.User;
import com.example.trader.exception.ConcurrentNodeUpdateException;
import com.example.trader.realtime.outbox.TransactionalReliableEventService;
import com.example.trader.repository.NodeHistoryRepository;
import com.example.trader.repository.NodeRepository;
import com.example.trader.service.event.TeamNodeChangeAfterCommitListener;
import com.example.trader.service.event.TeamNodeChangeCommittedEvent;
import com.example.trader.service.cache.CacheInvalidationRetryService;
import com.example.trader.service.cache.NodeQueryCacheInvalidator;
import com.example.trader.ws.raw.edit.NodeEditSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({
        NodeService.class,
        NodeChangeRequestValidator.class,
        TeamNodeChangeAfterCommitListener.class,
        NodeServiceTransactionContractTest.ObjectMapperConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NodeServiceTransactionContractTest {

    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();

    @Autowired NodeService nodeService;
    @Autowired NodeRepository nodeRepository;
    @Autowired NodeHistoryRepository nodeHistoryRepository;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApplicationEventPublisher eventPublisher;

    @MockitoBean NodeCacheService nodeCacheService;
    @MockitoBean GraphCacheService graphCacheService;
    @MockitoBean NodeConflictValidator conflictValidator;
    @MockitoBean NodeEditSessionService editSessionService;
    @MockitoBean TransactionalReliableEventService reliableEventService;
    @MockitoBean NodeQueryCacheInvalidator cacheInvalidator;
    @MockitoBean CacheInvalidationRetryService cacheInvalidationRetryService;

    @Test
    @DisplayName("Outbox 적재 실패 시 Node와 History DB 변경을 함께 롤백")
    void outbox_failure_rolls_back_node_and_history() {
        Fixture fixture = createFixture();
        when(conflictValidator.validateContent(any(), any(), any(), any(), any(Integer.class), any(), any()))
                .thenReturn(ConflictResult.pass(3));
        doThrow(new IllegalStateException("outbox save failed"))
                .when(reliableEventService).stage(any());

        UpdateTeamNodeContentRequest request = new UpdateTeamNodeContentRequest();
        request.setSubject("롤백될 제목");
        request.setDirtyFields(List.of("subject"));
        request.setBaseVersion(3);

        assertThatThrownBy(() -> nodeService.updateTeamNode(
                fixture.teamId(),
                fixture.graphId(),
                fixture.nodeId(),
                fixture.userId(),
                request
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("outbox save failed");

        Node reloaded = nodeRepository.findById(fixture.nodeId()).orElseThrow();
        assertThat(reloaded.getSubject()).isEqualTo("기존 제목");
        assertThat(reloaded.getVersion()).isEqualTo(3);
        assertThat(nodeHistoryRepository
                .findByNodeIdAndVersionGreaterThanOrderByVersionAsc(fixture.nodeId(), 0))
                .isEmpty();
    }

    @Test
    @DisplayName("커밋된 트랜잭션의 Node 후처리 이벤트만 실행")
    void after_commit_tasks_run_only_for_committed_transaction() {
        TeamNodeChangeCommittedEvent event =
                new TeamNodeChangeCommittedEvent(1L, 2L, 3L, 4L, 5, List.of("subject"));
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        org.mockito.Mockito.verifyNoInteractions(
                editSessionService,
                cacheInvalidator,
                cacheInvalidationRetryService
        );

        template.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        org.mockito.Mockito.verify(editSessionService)
                .saveVersionHint(1L, 2L, 3L, 5, List.of("subject"), 4L);
        org.mockito.Mockito.verify(editSessionService).endEditSession(1L, 2L, 3L, 4L);
        org.mockito.Mockito.verify(cacheInvalidator).invalidate(2L);
    }

    @Test
    @DisplayName("같은 Node 스냅샷을 수정한 두 트랜잭션 중 하나만 커밋되고 업무 version도 한 번만 증가")
    void optimistic_lock_prevents_lost_update() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch bothRequestsValidated = new CountDownLatch(2);
        CountDownLatch releaseWrites = new CountDownLatch(1);
        when(conflictValidator.validateContent(any(), any(), any(), any(), any(Integer.class), any(), any()))
                .thenAnswer(invocation -> {
                    bothRequestsValidated.countDown();
                    if (!releaseWrites.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시성 테스트 쓰기 대기 시간이 초과되었습니다.");
                    }
                    return ConflictResult.pass(3);
                });

        UpdateTeamNodeContentRequest first = subjectRequest("첫 번째 제목", 3);
        UpdateTeamNodeContentRequest second = subjectRequest("두 번째 제목", 3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> firstResult = executor.submit(() -> nodeService.updateTeamNode(
                    fixture.teamId(), fixture.graphId(), fixture.nodeId(), fixture.userId(), first
            ));
            Future<?> secondResult = executor.submit(() -> nodeService.updateTeamNode(
                    fixture.teamId(), fixture.graphId(), fixture.nodeId(), fixture.userId(), second
            ));

            assertThat(bothRequestsValidated.await(5, TimeUnit.SECONDS)).isTrue();
            releaseWrites.countDown();

            int successes = 0;
            int optimisticFailures = 0;
            for (Future<?> result : List.of(firstResult, secondResult)) {
                try {
                    result.get(10, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(ConcurrentNodeUpdateException.class);
                    optimisticFailures++;
                }
            }

            assertThat(successes).isEqualTo(1);
            assertThat(optimisticFailures).isEqualTo(1);
        } finally {
            releaseWrites.countDown();
            executor.shutdownNow();
        }

        Node reloaded = nodeRepository.findById(fixture.nodeId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(4);
        assertThat(nodeHistoryRepository
                .findByNodeIdAndVersionGreaterThanOrderByVersionAsc(fixture.nodeId(), 0))
                .hasSize(1);
        org.mockito.Mockito.verify(editSessionService, org.mockito.Mockito.times(1))
                .saveVersionHint(
                        fixture.teamId(),
                        fixture.graphId(),
                        fixture.nodeId(),
                        4,
                        List.of("subject"),
                        fixture.userId()
                );
        org.mockito.Mockito.verify(cacheInvalidator, org.mockito.Mockito.times(1))
                .invalidate(fixture.graphId());
    }

    private UpdateTeamNodeContentRequest subjectRequest(String subject, int baseVersion) {
        UpdateTeamNodeContentRequest request = new UpdateTeamNodeContentRequest();
        request.setSubject(subject);
        request.setDirtyFields(List.of("subject"));
        request.setBaseVersion(baseVersion);
        return request;
    }

    private Fixture createFixture() {
        int fixtureNumber = FIXTURE_SEQUENCE.incrementAndGet();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> {
            // 프로젝트의 Hibernate 플러그인 조합이 H2의 Node ID에 identity를 누락하므로
            // 운영 매핑을 바꾸지 않고 테스트 스키마에서만 생성 계약을 보정한다.
            entityManager.createNativeQuery(
                    "ALTER TABLE node ALTER COLUMN id BIGINT GENERATED BY DEFAULT AS IDENTITY"
            ).executeUpdate();
            entityManager.createNativeQuery(
                    "ALTER TABLE node ALTER COLUMN id RESTART WITH " + fixtureNumber
            ).executeUpdate();

            User user = User.builder()
                    .email("owner@example.com")
                    .username("owner")
                    .loginId("owner")
                    .password("password")
                    .nickName("owner")
                    .role(Role.USER)
                    .build();
            entityManager.persist(user);

            Team team = Team.builder()
                    .teamName("team-" + fixtureNumber)
                    .code(String.format("T%05d", fixtureNumber))
                    .build();
            entityManager.persist(team);

            Directory directory = Directory.builder().name("graph").team(team).build();
            entityManager.persist(directory);

            Page page = Page.builder().title("graph").directory(directory).user(user).build();
            entityManager.persist(page);

            Node node = Node.builder()
                    .x(0)
                    .y(0)
                    .subject("기존 제목")
                    .content("기존 내용")
                    .symb("AAPL")
                    .recordDate(LocalDate.of(2026, 9, 1))
                    .page(page)
                    .version(3)
                    .build();
            entityManager.persist(node);
            entityManager.flush();
            return new Fixture(team.getId(), page.getId(), node.getId(), user.getId());
        });
    }

    record Fixture(Long teamId, Long graphId, Long nodeId, Long userId) {
    }

    @TestConfiguration
    static class ObjectMapperConfiguration {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
