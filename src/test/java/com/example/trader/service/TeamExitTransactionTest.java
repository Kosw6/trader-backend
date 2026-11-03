package com.example.trader.service;

import com.example.trader.entity.Team;
import com.example.trader.entity.User;
import com.example.trader.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamExitTransactionTest {
    @Test
    void lazy_membership_removal_runs_inside_transaction_and_commits() {
        verifyBoundary(false);
    }

    @Test
    void failed_membership_removal_rolls_back_instead_of_partial_commit() {
        verifyBoundary(true);
    }

    private void verifyBoundary(boolean failure) {
        var teams = mock(TeamRepository.class);
        var users = mock(UserRepository.class);
        var team = mock(Team.class);
        var user = mock(User.class);
        var active = new AtomicBoolean();
        var committed = new AtomicBoolean();
        var rolledBack = new AtomicBoolean();
        PlatformTransactionManager manager = new PlatformTransactionManager() {
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                assertThat(definition.isReadOnly()).isFalse();
                active.set(true);
                return new SimpleTransactionStatus();
            }
            public void commit(TransactionStatus status) { committed.set(true); active.set(false); }
            public void rollback(TransactionStatus status) { rolledBack.set(true); active.set(false); }
        };
        when(teams.lockForMembership(22L)).thenAnswer(call -> {
            assertThat(active.get()).as("repository lookup must share the membership transaction").isTrue();
            return Optional.of(team);
        });
        when(users.findById(11L)).thenReturn(Optional.of(user));
        doAnswer(call -> {
            assertThat(active.get()).as("lazy collection mutation must run before commit").isTrue();
            if (failure) throw new IllegalStateException("synthetic removal failure");
            return null;
        }).when(team).removeMember(user);
        var service = new TeamService(teams, users, mock(UserTeamRepository.class),
                mock(NotifycationService.class), mock(JoinRequestRepository.class));
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var target = (TeamService) proxy.getProxy();
        if (failure) assertThatThrownBy(() -> target.exitTeam(11L, 22L)).isInstanceOf(IllegalStateException.class);
        else target.exitTeam(11L, 22L);
        verify(team).removeMember(user);
        assertThat(committed.get()).isEqualTo(!failure);
        assertThat(rolledBack.get()).isEqualTo(failure);
        assertThat(active.get()).isFalse();
    }
}
