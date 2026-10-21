package com.example.trader.service;

import com.example.trader.entity.*;
import com.example.trader.repository.*;
import com.example.trader.realtime.outbox.TransactionalReliableEventService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Isolated in-memory DB only: no Spring app config, user DB, Kafka, or backfill connection. */
class GroupChatPersistenceTest {
    LocalContainerEntityManagerFactoryBean factory;
    EntityManagerFactory emf;
    JpaTransactionManager tx;
    GroupChatRoomRepository rooms;
    GroupChatMessageRepository messages;
    GroupChatService service;
    TransactionalReliableEventService events;
    Long roomId;
    final String uuid="aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    @BeforeEach void setup() {
        factory=new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(new DriverManagerDataSource("jdbc:h2:mem:chat"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1", "sa", ""));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setManagedTypes(PersistenceManagedTypes.of(GroupChatRoom.class.getName(),GroupChatMessage.class.getName()));
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop",
                "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
                "hibernate.jdbc.time_zone","UTC"));
        factory.afterPropertiesSet();emf=Objects.requireNonNull(factory.getObject());tx=new JpaTransactionManager(emf);
        var repositories=new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
        rooms=repositories.getRepository(GroupChatRoomRepository.class);
        messages=repositories.getRepository(GroupChatMessageRepository.class);
        roomId=new TransactionTemplate(tx).execute(status -> rooms.saveAndFlush(GroupChatRoom.create(1L,"공용",true,10L)).getId());
        var members=mock(UserTeamRepository.class);when(members.existsByTeamIdAndUserId(1L,10L)).thenReturn(true);
        var users=mock(UserRepository.class);var sender=mock(User.class);when(sender.getNickName()).thenReturn("실제 ORM 닉네임");
        when(users.findById(10L)).thenReturn(Optional.of(sender));events=mock(TransactionalReliableEventService.class);
        var target=new GroupChatService(rooms,messages,members,mock(TeamRepository.class),users,events);
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));
        service=(GroupChatService)proxy.getProxy();
    }
    @AfterEach void cleanup(){ if(factory!=null) factory.destroy(); }

    @Test void actualJpaQueriesPersistTimeAndIdempotencyWithoutAnotherWrite() {
        var first=service.send(1L,roomId,10L,"메시지",uuid);
        var retry=service.send(1L,roomId,10L,"메시지",uuid.toUpperCase());
        assertThat(retry.id()).isEqualTo(first.id());assertThat(messages.count()).isEqualTo(1);
        var history=service.history(1L,roomId,10L,null);
        assertThat(history.messages()).hasSize(1);assertThat(history.nextBeforeId()).isNull();
        assertThat(history.messages().get(0).nickname()).isEqualTo("실제 ORM 닉네임");
        assertThat(history.messages().get(0).createdAt()).isNotNull();
        verify(events,times(1)).stage(any());
    }

    @Test void transactionReallyRollsBackMessageWhenOutboxStagingFails() {
        doThrow(new IllegalStateException("outbox failed")).when(events).stage(any());
        assertThatThrownBy(() -> service.send(1L,roomId,10L,"저장되면 안 됨",uuid)).isInstanceOf(IllegalStateException.class);
        assertThat(messages.count()).isZero();
    }

    @Test void historyCursorIsRoomScopedAndDoesNotRepeatMessages() {
        for(int i=0;i<55;i++) service.send(1L,roomId,10L,"message "+i,UUID.randomUUID().toString());
        var first=service.history(1L,roomId,10L,null);var older=service.history(1L,roomId,10L,first.nextBeforeId());
        assertThat(first.messages()).hasSize(50);assertThat(older.messages()).hasSize(5);
        assertThat(older.messages().get(4).id()).isLessThan(first.messages().get(0).id());
        assertThat(older.nextBeforeId()).isNull();
        assertThatThrownBy(() -> service.history(1L,roomId+999,10L,null)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
