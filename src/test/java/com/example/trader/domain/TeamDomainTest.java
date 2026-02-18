package com.example.trader.domain;


import com.example.trader.entity.Team;
import com.example.trader.entity.TeamRole;
import com.example.trader.entity.User;
import com.example.trader.entity.UserTeam;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.support.fixtures.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TeamDomainTest {
    @Test
    void OWNER는_한_팀에_한명만_가능하다() {
        Team team = TestFixtures.team();
        User owner1 = TestFixtures.userWithId();
        User owner2 = TestFixtures.userWithId();

        team.addMember(owner1, TeamRole.OWNER);

        assertThatThrownBy(() -> team.addMember(owner2, TeamRole.OWNER))
                .isInstanceOf(BaseException.class)
                .satisfies(e -> {
                    BaseException ex = (BaseException) e;
                    assertThat(ex.getStatus()).isEqualTo(BaseResponseStatus.TEAM_OWNER_CONFLICT);
                });
    }


    @Test
    @DisplayName("addMember는 팀에 UserTeam 링크를 추가하고, 유저의 userTeams에도 동기화한다")
    void addMember_adds_link_to_both_sides() {
        Team team = TestFixtures.team();
        User user = TestFixtures.userWithId();

        team.addMember(user, TeamRole.MEMBER);

        assertThat(team.getUserTeams()).hasSize(1);
        assertThat(user.getUserTeams()).hasSize(1);

        UserTeam link = team.getUserTeams().get(0);

        assertThat(link.getTeam()).isSameAs(team);
        assertThat(link.getUser()).isSameAs(user);
        assertThat(link.getRole()).isEqualTo(TeamRole.MEMBER);

        // 양방향 컬렉션에 동일 객체가 들어갔는지
        assertThat(user.getUserTeams().get(0)).isSameAs(link);
    }

    @Test
    @DisplayName("addMember는 같은 유저를 중복 추가하면 예외가 발생한다")
    void addMember_duplicate_user_throws() {
        Team team = TestFixtures.team();
        User user = TestFixtures.userWithId();

        team.addMember(user, TeamRole.MEMBER);

        assertThatThrownBy(() -> team.addMember(user, TeamRole.MEMBER))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("이미 팀에");
    }

    @Test
    @DisplayName("removeMember는 멤버를 제거하고 양쪽 컬렉션에서 링크가 삭제되며 link는 detach 된다")
    void removeMember_removes_link_and_detaches() {
        Team team = TestFixtures.team();
        User user = TestFixtures.userWithId();

        team.addMember(user, TeamRole.MEMBER);
        UserTeam link = team.getUserTeams().get(0);

        team.removeMember(user);

        assertThat(team.getUserTeams()).isEmpty();
        assertThat(user.getUserTeams()).isEmpty();

        // detach() 호출로 연관 끊김
        assertThat(link.getUser()).isNull();
        assertThat(link.getTeam()).isNull();
    }

    @Test
    @DisplayName("removeMember는 팀 멤버가 아닌 유저면 예외가 발생한다")
    void removeMember_not_member_throws() {
        Team team = TestFixtures.team();
        User user = TestFixtures.userWithId();

        assertThatThrownBy(() -> team.removeMember(user))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("팀 멤버가 아닙니다");
    }

    @Test
    @DisplayName("removeMember는 OWNER는 탈퇴할 수 없다")
    void removeMember_owner_cannot_exit() {
        Team team = TestFixtures.team();
        User user = TestFixtures.userWithId();

        team.addMember(user, TeamRole.OWNER);

        assertThatThrownBy(() -> team.removeMember(user))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("OWNER");
    }

    @Test
    @DisplayName("setTeamName은 팀명을 변경하고 자기 자신을 반환한다")
    void setTeamName_updates_and_returns_this() {
        Team team = TestFixtures.team();

        Team returned = team.setTeamName("newName");

        assertThat(returned).isSameAs(team);
        assertThat(team.getTeamName()).isEqualTo("newName");
    }
}
