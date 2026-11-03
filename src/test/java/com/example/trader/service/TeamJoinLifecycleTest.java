package com.example.trader.service;

import com.example.trader.entity.*;
import com.example.trader.repository.*;
import com.example.trader.support.fixtures.TestFixtures;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamJoinLifecycleTest {
    @Test void reapplication_has_new_identity_and_old_approval_cannot_approve_it() {
        var teams=mock(TeamRepository.class); var users=mock(UserRepository.class);
        var members=mock(UserTeamRepository.class); var requests=mock(JoinRequestRepository.class);
        var notifications=mock(NotifycationService.class);
        var service=new TeamService(teams,users,members,notifications,requests);
        var owner=TestFixtures.userWithId();var applicant=TestFixtures.userWithId();
        var team=TestFixtures.teamWithId();team.addMember(owner,TeamRole.OWNER);
        var old=TestFixtures.joinRequestWithId(applicant,team);old.approve();
        when(teams.findByCodeForUpdate(team.getCode())).thenReturn(Optional.of(team));
        when(teams.findOwnerUserIdByTeamId(team.getId())).thenReturn(Optional.of(owner.getId()));
        when(users.findById(applicant.getId())).thenReturn(Optional.of(applicant));
        when(requests.save(any())).thenAnswer(call -> call.getArgument(0));
        service.joinTeam(applicant.getId(),team.getCode());
        var captured=org.mockito.ArgumentCaptor.forClass(JoinRequest.class);
        verify(requests).save(captured.capture());var fresh=captured.getValue();
        assertThat(fresh).isNotSameAs(old);assertThat(fresh.getId()).isNull();
        assertThat(fresh.isPending()).isTrue();assertThat(old.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
        when(requests.findTeamId(old.getId())).thenReturn(Optional.of(team.getId()));
        when(teams.lockForMembership(team.getId())).thenReturn(Optional.of(team));
        when(requests.findWithTeamAndRequesterForUpdate(old.getId())).thenReturn(Optional.of(old));
        when(members.existsByTeamIdAndUserIdAndRole(team.getId(),owner.getId(),TeamRole.OWNER)).thenReturn(true);
        service.checkTeamRequest(owner.getId(),old.getId(),JoinRequestStatus.APPROVED);
        assertThat(fresh.isPending()).isTrue();assertThat(team.getUserTeams()).hasSize(1);
        verify(notifications,never()).notifyTeamJoinApproved(anyLong(),anyLong(),anyString());
    }

    @Test void deletion_locks_team_then_removes_its_requests_before_parent() {
        var teams=mock(TeamRepository.class);var requests=mock(JoinRequestRepository.class);
        var service=new TeamService(teams,mock(UserRepository.class),mock(UserTeamRepository.class),mock(NotifycationService.class),requests);
        var team=TestFixtures.teamWithId();when(teams.lockForMembership(team.getId())).thenReturn(Optional.of(team));
        service.deleteTeam(team.getId());
        var order=inOrder(teams,requests);order.verify(teams).lockForMembership(team.getId());
        order.verify(requests).deleteAllForTeam(team.getId());
        order.verify(teams).deleteOwnedDirectories(team.getId());order.verify(teams).delete(team);
    }

    @Test void failed_canvas_cleanup_cannot_continue_to_delete_parent() {
        var teams=mock(TeamRepository.class);var requests=mock(JoinRequestRepository.class);
        var service=new TeamService(teams,mock(UserRepository.class),mock(UserTeamRepository.class),mock(NotifycationService.class),requests);
        var team=TestFixtures.teamWithId();when(teams.lockForMembership(team.getId())).thenReturn(Optional.of(team));
        doThrow(new IllegalStateException("synthetic cleanup failure")).when(teams).deleteOwnedDirectories(team.getId());
        assertThatThrownBy(()->service.deleteTeam(team.getId())).isInstanceOf(IllegalStateException.class);
        verify(teams,never()).delete(any(Team.class));
    }
}
