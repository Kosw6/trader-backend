package com.example.trader.service;

import com.example.trader.entity.*;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.*;
import com.example.trader.controller.TeamController;
import com.example.trader.common.GlobalExceptionHandler;
import com.example.trader.dto.ResponseUserDto;
import com.example.trader.dto.ResponseNotifycationDto;
import com.example.trader.security.details.UserContext;
import com.example.trader.support.fixtures.TestFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TeamJoinDecisionTest {
    @Mock TeamRepository teams;
    @Mock UserRepository users;
    @Mock UserTeamRepository members;
    @Mock NotifycationService notifications;
    @Mock JoinRequestRepository requests;
    @InjectMocks TeamService service;

    record Fixture(User owner, User requester, Team team, JoinRequest request) {}

    Fixture fixture(boolean ownerAllowed) {
        var owner = TestFixtures.userWithId();
        var requester = TestFixtures.userWithId();
        var team = TestFixtures.teamWithId();
        team.addMember(owner, TeamRole.OWNER);
        var request = TestFixtures.joinRequestWithId(requester, team);
        when(requests.findTeamId(request.getId())).thenReturn(Optional.of(team.getId()));
        when(teams.lockForMembership(team.getId())).thenReturn(Optional.of(team));
        when(requests.findWithTeamAndRequesterForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(members.existsByTeamIdAndUserIdAndRole(team.getId(), owner.getId(), TeamRole.OWNER)).thenReturn(ownerAllowed);
        return new Fixture(owner, requester, team, request);
    }

    @ParameterizedTest
    @EnumSource(value=JoinRequestStatus.class, names={"APPROVED","REJECTED"})
    void same_decision_retry_does_not_add_members_or_notifications_twice(JoinRequestStatus status) {
        var f = fixture(true);
        service.checkTeamRequest(f.owner.getId(), f.request.getId(), status);
        service.checkTeamRequest(f.owner.getId(), f.request.getId(), status);
        assertThat(f.request.getStatus()).isEqualTo(status);
        assertThat(f.team.getUserTeams().stream().filter(x -> x.getUser().getId().equals(f.requester.getId())).count())
                .isEqualTo(status==JoinRequestStatus.APPROVED ? 1 : 0);
        if(status==JoinRequestStatus.APPROVED)
            verify(notifications).notifyTeamJoinApproved(f.requester.getId(),f.team.getId(),f.team.getTeamName());
        else verify(notifications).notifyTeamJoinRejected(f.requester.getId(),f.team.getId(),f.team.getTeamName());
        verify(notifications).markJoinRequestNotificationAsRead(f.owner.getId(),f.request.getId());
        verifyNoMoreInteractions(notifications);
    }

    @ParameterizedTest
    @EnumSource(value=JoinRequestStatus.class, names={"APPROVED","REJECTED"})
    void opposite_decision_returns_conflict_without_reversing_the_decision(JoinRequestStatus first) {
        var f=fixture(true);
        service.checkTeamRequest(f.owner.getId(),f.request.getId(),first);
        clearInvocations(notifications);
        var other=first==JoinRequestStatus.APPROVED ? JoinRequestStatus.REJECTED : JoinRequestStatus.APPROVED;
        assertThatThrownBy(() -> service.checkTeamRequest(f.owner.getId(),f.request.getId(),other))
                .isInstanceOfSatisfying(BaseException.class,e -> assertThat(e.getStatus()).isEqualTo(BaseResponseStatus.JOIN_REQUEST_ALREADY_PROCESSED));
        assertThat(f.request.getStatus()).isEqualTo(first);
        verifyNoInteractions(notifications);
    }

    @ParameterizedTest
    @EnumSource(value=JoinRequestStatus.class, names={"APPROVED","REJECTED"})
    void retry_still_requires_owner_permission(JoinRequestStatus status) {
        var f=fixture(false);
        if(status==JoinRequestStatus.APPROVED) f.request.approve(); else f.request.reject();
        assertThatThrownBy(() -> service.checkTeamRequest(f.owner.getId(),f.request.getId(),status))
                .isInstanceOfSatisfying(BaseException.class,e -> assertThat(e.getStatus()).isEqualTo(BaseResponseStatus.ACCESS_DENIED));
        verifyNoInteractions(notifications);
    }

    @Test
    void a_late_approval_retry_does_not_rejoin_a_member_who_has_left() {
        var f=fixture(true);
        service.checkTeamRequest(f.owner.getId(),f.request.getId(),JoinRequestStatus.APPROVED);
        f.team.removeMember(f.requester);
        service.checkTeamRequest(f.owner.getId(),f.request.getId(),JoinRequestStatus.APPROVED);
        assertThat(f.team.getUserTeams()).noneMatch(x -> x.getUser().getId().equals(f.requester.getId()));
        verify(notifications,times(1)).notifyTeamJoinApproved(anyLong(),anyLong(),anyString());
    }

    @Test
    void invalid_decisions_do_not_touch_the_repository() {
        for(JoinRequestStatus status:new JoinRequestStatus[]{null,JoinRequestStatus.PENDING}) {
            assertThatThrownBy(() -> service.checkTeamRequest(1L,2L,status))
                    .isInstanceOfSatisfying(BaseException.class,e -> assertThat(e.getStatus()).isEqualTo(BaseResponseStatus.INVALID_REQUEST));
        }
        verifyNoInteractions(requests,members,notifications);
    }

    @Test
    void unknown_request_is_not_reported_as_a_successful_retry() {
        when(requests.findTeamId(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.checkTeamRequest(1L,2L,JoinRequestStatus.APPROVED))
                .isInstanceOfSatisfying(BaseException.class,e -> assertThat(e.getStatus()).isEqualTo(BaseResponseStatus.JOIN_REQUEST_NOT_FOUND));
        verifyNoInteractions(members,notifications);
    }

    @Test
    void terminal_requests_are_also_locked_inside_the_service_transaction() throws Exception {
        var method=JoinRequestRepository.class.getMethod("findWithTeamAndRequesterForUpdate",Long.class);
        assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(method.getAnnotation(Query.class).value()).doesNotContain("jr.status");
        assertThat(TeamService.class.getMethod("checkTeamRequest",Long.class,Long.class,JoinRequestStatus.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value=JoinRequestStatus.class,names={"APPROVED","REJECTED"})
    void controller_returns_empty204_for_both_first_decision_and_same_retry(JoinRequestStatus decision) throws Exception {
        var f=fixture(true);
        var principal=mock(UserContext.class);
        var userDto=mock(ResponseUserDto.class);
        when(principal.getUserDto()).thenReturn(userDto);
        when(userDto.getId()).thenReturn(f.owner.getId());
        var mvc=standaloneSetup(new TeamController(service,mock(UserTeamService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter p) {
                        return p.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }
                    public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,
                            NativeWebRequest r,WebDataBinderFactory b) { return principal; }
                }).build();
        String endpoint="/api/team/teams/join-requests/"+f.request.getId();
        String body="{\"status\":\""+decision+"\"}";
        for(int i=0;i<2;i++) mvc.perform(patch(endpoint).contentType("application/json").content(body))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        var other=decision==JoinRequestStatus.APPROVED ? JoinRequestStatus.REJECTED : JoinRequestStatus.APPROVED;
        mvc.perform(patch(endpoint).contentType("application/json").content("{\"status\":\""+other+"\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("JOIN_REQUEST_ALREADY_PROCESSED"));
    }

    @Test
    void notification_boolean_serializes_as_read_for_the_frontend_compatibility_contract() throws Exception {
        var dto=ResponseNotifycationDto.builder().id(1L).isRead(true).type(NotifycationType.TEAM_JOIN_REQUESTED).build();
        var json=new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(dto));
        assertThat(json.path("read").asBoolean()).isTrue();
    }

    @Test
    void deletion_uses_path_team_even_when_a_conflicting_query_parameter_is_supplied() throws Exception {
        var principal=mock(UserContext.class);var dto=mock(ResponseUserDto.class);
        when(principal.getUserDto()).thenReturn(dto);when(dto.getId()).thenReturn(1L);
        var teamService=mock(TeamService.class);var access=mock(UserTeamService.class);
        var mvc=standaloneSetup(new TeamController(teamService,access))
            .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                public boolean supportsParameter(MethodParameter p) {return p.hasParameterAnnotation(AuthenticationPrincipal.class);}
                public Object resolveArgument(MethodParameter p,ModelAndViewContainer m,NativeWebRequest r,WebDataBinderFactory b) {return principal;}
            }).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/team/11").param("teamId","99"))
            .andExpect(status().isNoContent());
        verify(access).checkTeamAuthWithOwner(1L,11L);verify(teamService).deleteTeam(11L);
        verifyNoMoreInteractions(access,teamService);
    }
}
