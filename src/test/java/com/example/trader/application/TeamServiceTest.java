package com.example.trader.application;

import com.example.trader.dto.RequestTeamNameDto;
import com.example.trader.entity.*;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.repository.*;
import com.example.trader.service.NotifycationService;

import com.example.trader.service.TeamService;
import com.example.trader.support.fixtures.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    TeamRepository teamRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    UserTeamRepository utRepository;

    @Mock
    NotifycationService notificationService;
    @Mock
    JoinRequestRepository jqRepository;

    @InjectMocks
    TeamService teamService;


    @Test
    @DisplayName("팀 생성시 생성자는 OWNER로 추가된다")
    void createTeam_shouldAddCreatorAsOwner() {
        // given
        User owner = TestFixtures.userWithId();
        given(userRepository.findById(owner.getId())).willReturn(Optional.of(owner));

        // saveAndFlush 2번 호출되는 구조이므로, id 세팅해서 반환
        given(teamRepository.saveAndFlush(any(Team.class)))
                .willAnswer(inv -> {
                    Team t = inv.getArgument(0);
                    if (t.getId() == null) {
                        ReflectionTestUtils.setField(t, "id", 100L);
                    }
                    return t;
                });

        ArgumentCaptor<Team> teamCaptor = ArgumentCaptor.forClass(Team.class);

        // when
        Team team = teamService.createTeam(owner.getId(), new RequestTeamNameDto("teamName"));

        // then
        assertThat(team.getId()).isEqualTo(100L);

        then(userRepository).should().findById(owner.getId());
        then(teamRepository).should(times(2)).saveAndFlush(teamCaptor.capture());

        // 2번째 saveAndFlush 때는 addMember가 이미 수행된 상태여야 함
        Team savedWithOwner = teamCaptor.getAllValues().get(1);

        assertThat(savedWithOwner.getUserTeams()).anySatisfy(ut -> {
            assertThat(ut.getUser().getId()).isEqualTo(owner.getId());
            assertThat(ut.getRole()).isEqualTo(TeamRole.OWNER);
        });
    }

    @Test
    @DisplayName("코드 유니크 충돌시 재시도한다")
    void createTeam_shouldRetry_whenCodeUniqueConflictOccurs() {
        // given
        User owner = TestFixtures.userWithId();

        given(userRepository.findById(owner.getId()))
                .willReturn(Optional.of(owner));

        given(teamRepository.saveAndFlush(any(Team.class)))
                .willThrow(new DataIntegrityViolationException("dup")) // 시도1: 코드 충돌
                .willAnswer(inv -> inv.getArgument(0))                 // 시도2: 팀 저장 성공(멤버추가 전)
                .willAnswer(inv -> inv.getArgument(0));                // 시도3: 멤버추가 후 저장

        // when & then
        assertThatCode(() ->
                teamService.createTeam(owner.getId(), new RequestTeamNameDto("teamName"))
        ).doesNotThrowAnyException();

        // then
        then(teamRepository).should(times(3)).saveAndFlush(any(Team.class));
        then(userRepository).should().findById(owner.getId());
    }

    @Test
    @DisplayName("코드 생성이 5번 연속 실패하면 예외가 발생한다.")
    void createTeam_shouldThrowException_whenCodeGenerationFailsFiveTimes() {
        // given
        User owner = TestFixtures.userWithId();
        given(userRepository.findById(owner.getId()))
                .willReturn(Optional.of(owner));

        given(teamRepository.saveAndFlush(any(Team.class)))
                .willThrow(new DataIntegrityViolationException("dup"))
                .willThrow(new DataIntegrityViolationException("dup"))
                .willThrow(new DataIntegrityViolationException("dup"))
                .willThrow(new DataIntegrityViolationException("dup"))
                .willThrow(new DataIntegrityViolationException("dup"));

        // when / then
        assertThatThrownBy(() ->
                teamService.createTeam(owner.getId(), new RequestTeamNameDto("teamName"))
        ).isInstanceOf(BaseException.class)
                .hasMessageContaining("팀 코드 생성에 실패");

        // then
        then(teamRepository).should(times(5)).saveAndFlush(any(Team.class));
        then(userRepository).should().findById(owner.getId());
    }

    @Test
    @DisplayName("팀 가입 요청 성공시 JoinRequest를 생성하고 OWNER에게 알림을 전송한다")
    void joinTeam_shouldCreateJoinRequestAndNotifyOwner_whenValidRequest() {
        // given
        User requester = TestFixtures.userWithId();
        Team team = TestFixtures.teamWithId();
        String code = team.getCode();

        User owner = TestFixtures.userWithId();
        Long ownerId = owner.getId();

        given(teamRepository.findByCode(code)).willReturn(Optional.of(team));
        given(teamRepository.findOwnerUserIdByTeamId(team.getId())).willReturn(Optional.of(ownerId));
        given(userRepository.findById(requester.getId())).willReturn(Optional.of(requester));

        given(utRepository.existsByTeamIdAndUserId(team.getId(), requester.getId())).willReturn(false);
        given(jqRepository.existsByRequesterIdAndTeamIdAndStatus(
                requester.getId(), team.getId(), JoinRequestStatus.PENDING
        )).willReturn(false);


        given(jqRepository.save(any(JoinRequest.class))).willAnswer(inv -> {
            JoinRequest jr = inv.getArgument(0);
            ReflectionTestUtils.setField(jr, "id", 777L);
            return jr;
        });

        ArgumentCaptor<JoinRequest> captor = ArgumentCaptor.forClass(JoinRequest.class);


        // when
        teamService.joinTeam(requester.getId(), code);

        // then
        then(jqRepository).should().save(captor.capture());
        JoinRequest saved = captor.getValue();
        then(notificationService).should().notifyTeamJoinRequested(
                eq(ownerId),
                eq(team.getId()),
                eq(saved.getId()),      // == 777L
                eq(requester.getId()),
                eq(team.getTeamName()),
                eq(requester.getNickName())
        );
        then(notificationService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("이미 팀원인 경우 가입 요청시 예외가 발생한다")
    void joinTeam_shouldThrowException_whenUserAlreadyMember() {
        // given
        User requester = TestFixtures.userWithId();
        Team team = TestFixtures.teamWithId();
        String code = team.getCode();

        User owner = TestFixtures.userWithId();
        given(teamRepository.findByCode(code)).willReturn(Optional.of(team));
        given(teamRepository.findOwnerUserIdByTeamId(team.getId())).willReturn(Optional.of(owner.getId()));
        given(userRepository.findById(requester.getId())).willReturn(Optional.of(requester));

        given(utRepository.existsByTeamIdAndUserId(team.getId(), requester.getId())).willReturn(true);

        // when / then
        assertThatThrownBy(() -> teamService.joinTeam(requester.getId(), code))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ALREADY_TEAM_MEMBER));

        then(jqRepository).should(never()).save(any());
        then(notificationService).should(never())
                .notifyTeamJoinRequested(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("이미 대기중인 가입 요청이 있으면 추가 요청을 생성하지 않는다")
    void joinTeam_shouldDoNothing_whenPendingRequestAlreadyExists() {
        // given
        User requester = TestFixtures.userWithId();
        Team team = TestFixtures.teamWithId();
        String code = team.getCode();

        User owner = TestFixtures.userWithId();

        given(teamRepository.findByCode(code)).willReturn(Optional.of(team));
        given(teamRepository.findOwnerUserIdByTeamId(team.getId())).willReturn(Optional.of(owner.getId()));
        given(userRepository.findById(requester.getId())).willReturn(Optional.of(requester));

        given(utRepository.existsByTeamIdAndUserId(team.getId(), requester.getId())).willReturn(false);
        given(jqRepository.existsByRequesterIdAndTeamIdAndStatus(
                requester.getId(), team.getId(), JoinRequestStatus.PENDING
        )).willReturn(true);

        // when
        teamService.joinTeam(requester.getId(), code);

        // then
        then(jqRepository).should(never()).save(any());
        then(notificationService).should(never())
                .notifyTeamJoinRequested(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }



    @Test
    @DisplayName("OWNER가 가입 요청을 승인하면 팀에 MEMBER가 추가되고 승인 알림이 전송된다")
    void checkTeamRequest_shouldApproveAndAddMember_whenOwnerApproves() {
        // given
        Team team = TestFixtures.teamWithId();
        User owner = TestFixtures.userWithId();
        User requester = TestFixtures.userWithId();

        // 팀에 OWNER 미리 존재(도메인 규칙)
        team.addMember(owner, TeamRole.OWNER);

        JoinRequest pendingReq = TestFixtures.joinRequestWithId(requester, team);

        given(teamRepository.findById(team.getId())).willReturn(Optional.of(team));
        given(utRepository.existsByTeamIdAndUserIdAndRole(team.getId(), owner.getId(), TeamRole.OWNER))
                .willReturn(true);
        given(jqRepository.findPending(requester.getId(), team.getId()))
                .willReturn(Optional.of(pendingReq));
        given(userRepository.findById(requester.getId())).willReturn(Optional.of(requester));

        // when
        teamService.checkTeamRequest(owner.getId(), requester.getId(), pendingReq.getStatus());

        // then (MEMBER 추가됨)
        assertThat(team.getUserTeams().stream()
                .anyMatch(ut -> ut.getUser().getId().equals(requester.getId())
                        && ut.getRole() == TeamRole.MEMBER))
                .isTrue();

        then(notificationService).should()
                .notifyTeamJoinApproved(requester.getId(), team.getId(), team.getTeamName());
        then(notificationService).should()
                .markJoinRequestNotificationAsRead(owner.getId(), pendingReq.getId());
        assertThat(pendingReq.getStatus()).isEqualTo(JoinRequestStatus.APPROVED);
    }

    @Test
    @DisplayName("OWNER가 가입 요청을 거절하면 MEMBER 추가 없이 거절 알림이 전송된다")
    void checkTeamRequest_shouldRejectRequest_whenOwnerRejects() {
        // given
        Team team = TestFixtures.teamWithId();
        User owner = TestFixtures.userWithId();
        User requester = TestFixtures.userWithId();

        team.addMember(owner, TeamRole.OWNER);
        JoinRequest pendingReq = TestFixtures.joinRequestWithId(requester, team);

        given(teamRepository.findById(team.getId())).willReturn(Optional.of(team));
        given(utRepository.existsByTeamIdAndUserIdAndRole(team.getId(), owner.getId(), TeamRole.OWNER))
                .willReturn(true);
        given(jqRepository.findPending(requester.getId(), team.getId()))
                .willReturn(Optional.of(pendingReq));

        // when
        teamService.checkTeamRequest(owner.getId(), requester.getId(), pendingReq.getStatus());

        // then (requester가 팀에 추가되지 않아야)
        assertThat(team.getUserTeams().stream()
                .anyMatch(ut -> ut.getUser().getId().equals(requester.getId())))
                .isFalse();

        then(notificationService).should()
                .notifyTeamJoinRejected(requester.getId(), team.getId(), team.getTeamName());
        then(notificationService).should()
                .markJoinRequestNotificationAsRead(owner.getId(), pendingReq.getId());

        then(userRepository).should(never()).findById(requester.getId()); // 거절시엔 조회 안 함(현재 코드)
        assertThat(pendingReq.getStatus()).isEqualTo(JoinRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("OWNER가 아닌 사용자가 가입 요청을 처리하면 예외가 발생한다")
    void checkTeamRequest_shouldThrowException_whenUserIsNotOwner() {
        // given
        Team team = TestFixtures.teamWithId();
        User notOwner = TestFixtures.userWithId();
        User requester = TestFixtures.userWithId();

        given(teamRepository.findById(team.getId())).willReturn(Optional.of(team));
        given(utRepository.existsByTeamIdAndUserIdAndRole(team.getId(), notOwner.getId(), TeamRole.OWNER))
                .willReturn(false);

        // when / then
        assertThatThrownBy(() -> teamService.checkTeamRequest(notOwner.getId(), requester.getId(),JoinRequestStatus.PENDING))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.ACCESS_DENIED));

        then(jqRepository).should(never()).findPending(anyLong(), anyLong());
        then(notificationService).should(never()).notifyTeamJoinApproved(anyLong(), anyLong(), anyString());
        then(notificationService).should(never()).notifyTeamJoinRejected(anyLong(), anyLong(), anyString());
        then(notificationService).should(never()).markJoinRequestNotificationAsRead(anyLong(), anyLong());
    }

    @Test
    @DisplayName("대기중인 가입 요청이 없으면 예외가 발생한다")
    void checkTeamRequest_shouldThrowException_whenJoinRequestNotFound() {
        // given
        Team team = TestFixtures.teamWithId();
        User owner = TestFixtures.userWithId();
        User requester = TestFixtures.userWithId();

        given(teamRepository.findById(team.getId())).willReturn(Optional.of(team));
        given(utRepository.existsByTeamIdAndUserIdAndRole(team.getId(), owner.getId(), TeamRole.OWNER))
                .willReturn(true);
        given(jqRepository.findPending(requester.getId(), team.getId()))
                .willReturn(Optional.empty());

        // when / then
        //요청이 없을 때 오너가 리퀘스터의 요청을 승인하려고 함
        assertThatThrownBy(() -> teamService.checkTeamRequest(owner.getId(), requester.getId(), JoinRequestStatus.APPROVED))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.JOIN_REQUEST_NOT_FOUND));

        then(notificationService).should(never()).notifyTeamJoinApproved(anyLong(), anyLong(), anyString());
        then(notificationService).should(never()).notifyTeamJoinRejected(anyLong(), anyLong(), anyString());
        then(notificationService).should(never()).markJoinRequestNotificationAsRead(anyLong(), anyLong());
    }


    @Test
    @DisplayName("팀 탈퇴시 팀에서 해당 사용자가 제거된다")
    void exitTeam_shouldRemoveMember_whenValidRequest() {
        // given
        Team team = TestFixtures.teamWithId();
        User owner = TestFixtures.userWithId();
        User member = TestFixtures.userWithId();

        team.addMember(owner, TeamRole.OWNER);
        team.addMember(member, TeamRole.MEMBER);

        given(teamRepository.findById(team.getId())).willReturn(Optional.of(team));
        given(userRepository.findById(member.getId())).willReturn(Optional.of(member));

        // when
        teamService.exitTeam(member.getId(), team.getId());

        // then
        assertThat(team.getUserTeams().stream()
                .anyMatch(ut -> ut.getUser().getId().equals(member.getId())))
                .isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 팀 또는 유저로 탈퇴 요청시 예외가 발생한다")
    void exitTeam_shouldThrowException_whenTeamOrUserNotFound() {
        // case1: team not found
        User member = TestFixtures.userWithId();
        Team team = TestFixtures.teamWithId();

        given(teamRepository.findById(team.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.exitTeam(member.getId(), team.getId()))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.TEAM_NOT_FOUND));

        // case2: user not found
        given(teamRepository.findById(team.getId())).willReturn(Optional.of(team));
        given(userRepository.findById(member.getId())).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.exitTeam(member.getId(), team.getId()))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> assertThat(((BaseException) e).getStatus())
                        .isEqualTo(BaseResponseStatus.USER_NOT_FOUND));
    }


}
