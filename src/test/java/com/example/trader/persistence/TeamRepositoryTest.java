package com.example.trader.persistence;

import com.example.trader.dto.TeamSummaryDto;
import com.example.trader.entity.Team;
import com.example.trader.entity.TeamRole;
import com.example.trader.entity.User;
import com.example.trader.entity.UserTeam;
import com.example.trader.repository.TeamRepository;
import com.example.trader.repository.UserRepository;
import com.example.trader.repository.UserTeamRepository;
import com.example.trader.support.RepositoryTestBase;
import com.example.trader.support.fixtures.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class TeamRepositoryTest extends RepositoryTestBase {
    @Autowired TeamRepository teamRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    UserTeamRepository userTeamRepository;


    @Test
    @DisplayName("팀 상세 조회시 팀-유저가 fetch join으로 함께 조회된다")
    void findByIdWithMembers_shouldFetchUsers() {
        // given
        User u1 = userRepository.save(TestFixtures.user());
        User u2 = userRepository.save(TestFixtures.user());

        Team team = teamRepository.save(Team.builder().teamName("t1").code("code1").build());
        team.addMember(u1, TeamRole.OWNER);
        team.addMember(u2, TeamRole.MEMBER);
        teamRepository.save(team);

        em.flush();
        em.clear(); // 영속성 컨텍스트 비워서 진짜 쿼리로 확인

        // when
        Team found = teamRepository.findByIdWithMembers(team.getId()).orElseThrow();

        // then
        assertThat(found.getUserTeams()).hasSize(2);
        assertThat(found.getUserTeams())
                .allSatisfy(ut -> assertThat(ut.getUser().getUsername()).isNotBlank());
    }

    @Test
    @DisplayName("자신이 속한 팀이 여러 개면 모두 조회되고, 멤버수/오너/정렬(createdAt desc)이 정확하다")
    void findMyTeamsSummary_shouldReturnAllTeamsWithCorrectCountAndOrder() {
        // given
        User me = userRepository.save(TestFixtures.user());

        // Team A (older)
        User ownerA = userRepository.save(TestFixtures.user());
        User extraA = userRepository.save(TestFixtures.user());
        Team teamA = teamRepository.save(Team.builder().teamName("teamA").code("codeA").build());
        teamA.addMember(ownerA, TeamRole.OWNER);
        teamA.addMember(me, TeamRole.MEMBER);
        teamA.addMember(extraA, TeamRole.MEMBER);
        teamRepository.save(teamA);

        // Team B (newer)
        User ownerB = userRepository.save(TestFixtures.user());
        Team teamB = teamRepository.save(Team.builder().teamName("teamB").code("codeB").build());
        teamB.addMember(ownerB, TeamRole.OWNER);
        teamB.addMember(me, TeamRole.MEMBER);
        teamRepository.save(teamB);

        // createdAt 정렬이 테스트에서 안정적으로 되게끔 강제로 시간 차이를 부여
        // (BaseTimeEntity.createdAt 필드명 기준. 다르면 "createdAt" 문자열만 바꾸면 됨)
        ReflectionTestUtils.setField(teamA, "createdAt", LocalDateTime.now().minusDays(1));
        ReflectionTestUtils.setField(teamB, "createdAt", LocalDateTime.now());

        em.flush();
        em.clear();

        // when
        List<TeamSummaryDto> result = teamRepository.findMyTeamsSummary(me.getId());

        // then
        assertThat(result).hasSize(2);

        TeamSummaryDto first = result.get(0);
        TeamSummaryDto second = result.get(1);

        // createdAt desc -> teamB가 먼저
        assertThat(first.teamName()).isEqualTo("teamB");
        assertThat(first.ownerName()).isEqualTo(ownerB.getUsername());
        assertThat(first.memberCount()).isEqualTo(2);

        assertThat(second.teamName()).isEqualTo("teamA");
        assertThat(second.ownerName()).isEqualTo(ownerA.getUsername());
        assertThat(second.memberCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("존재하지 않는 팀 아이디로 조회하면 오너 유저 아이디는 empty다")
    void findOwnerUserIdByTeamId_shouldReturnEmpty_whenTeamNotExists() {
        // when
        Optional<Long> ownerId = teamRepository.findOwnerUserIdByTeamId(999999L);

        // then
        assertThat(ownerId).isEmpty();
    }

    @Test
    @DisplayName("팀은 존재하지만 OWNER가 없으면 오너 유저 아이디는 empty다")
    void findOwnerUserIdByTeamId_shouldReturnEmpty_whenOwnerNotExists() {
        // given
        User member = userRepository.save(TestFixtures.user());
        Team team = teamRepository.save(TestFixtures.team());
        team.addMember(member, TeamRole.MEMBER); // OWNER 없이 MEMBER만 존재
        Long teamId = teamRepository.save(team).getId();

        em.flush();
        em.clear();

        // when
        Optional<Long> ownerId = teamRepository.findOwnerUserIdByTeamId(teamId);

        // then
        assertThat(ownerId).isEmpty();
    }

    @Test
    @DisplayName("자신이 속한 팀 조회시 속한 팀의 정보와 해당 팀의 오너가 조회된다.")
    void findMyTeamsSummary_shouldReturnTeamSummaryWithOwnerName(){
        //given
        User owner = userRepository.save(TestFixtures.user());
        User member = userRepository.save(TestFixtures.user());

        Team team = teamRepository.save(TestFixtures.team());
        team.addMember(owner, TeamRole.OWNER);
        team.addMember(member, TeamRole.MEMBER);
        teamRepository.save(team);

        em.flush();
        em.clear();

        //when
        List<TeamSummaryDto> result =
                teamRepository.findMyTeamsSummary(member.getId());
        assertThat(result).hasSize(1);

        TeamSummaryDto dto = result.get(0);

        assertThat(dto.teamName()).isEqualTo(team.getTeamName());
        assertThat(dto.ownerName()).isEqualTo(owner.getUsername());
        assertThat(dto.memberCount()).isEqualTo(2);

    }

    @Test
    @DisplayName("팀 아이디를 받아 해당 팀의 오너의 유저 아이디를 반환한다.")
    void findOwnerUserIdByTeamId_shouldReturnOwnerId_whenTeamExists(){
        //given
        User owner = userRepository.save(TestFixtures.user());
        Team team = teamRepository.save(TestFixtures.team());
        team.addMember(owner, TeamRole.OWNER);
        Long teamId = teamRepository.save(team).getId();

        em.flush();
        em.clear();
        //when
        Long ownerId = teamRepository.findOwnerUserIdByTeamId(teamId).get();
        User user = userRepository.findById(ownerId).get();
        //then
        assertThat(user.getUsername()).isEqualTo(owner.getUsername());
        assertThat(user.getEmail()).isEqualTo(owner.getEmail());
    }

    @Test
    @DisplayName("오너가 아닌 유저의 팀 역할을 변경한다 + 쿼리 후 재조회로 검증")
    void updateMemberRole_shouldChangeUserTeamRole_whenUserNotOwner() {
        // given
        User owner = userRepository.save(TestFixtures.user());
        User member = userRepository.save(TestFixtures.user());
        Team team = teamRepository.save(TestFixtures.team());
        team.addMember(owner, TeamRole.OWNER);
        team.addMember(member, TeamRole.MEMBER);
        teamRepository.save(team);

        em.flush();
        em.clear();

        // when
        teamRepository.updateMemberRole(member.getId(), team.getId(), TeamRole.MANAGER);

        // 수정쿼리는 영속성 컨텍스트와 DB 불일치가 흔해서 여기서 반드시 정리
        em.flush();
        em.clear();

        // then
        Team findTeam = teamRepository.findByIdWithMembers(team.getId()).orElseThrow();
        UserTeam findUt = findTeam.getUserTeams().stream()
                .filter(ut -> ut.getUser().getId().equals(member.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(findUt.getRole()).isEqualTo(TeamRole.MANAGER);
    }
}

