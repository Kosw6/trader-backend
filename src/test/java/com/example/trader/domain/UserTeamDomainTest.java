package com.example.trader.domain;

import com.example.trader.entity.Team;
import com.example.trader.entity.TeamRole;
import com.example.trader.entity.User;
import com.example.trader.entity.UserTeam;
import com.example.trader.exception.BaseException;
import com.example.trader.httpresponse.BaseResponseStatus;
import com.example.trader.support.fixtures.TestFixtures;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserTeamDomainTest {


    @Test
    @DisplayName("UserTeam역할 변경")
    void changeRole_changes_role() {
        User user = TestFixtures.user();
        Team team = TestFixtures.team();

        UserTeam userTeam = UserTeam.create(user, team, TeamRole.MEMBER);

        userTeam.changeRole(TeamRole.MANAGER); // 너가 새 역할 추가했다고 가정

        assertEquals(TeamRole.MANAGER, userTeam.getRole());
    }

    @Test
    @DisplayName("UserTeam.create는 user, team, role을 정상적으로 설정한다")
    void create_sets_fields_correctly() {

        User user = TestFixtures.user();
        Team team = TestFixtures.team();

        UserTeam ut = UserTeam.create(user, team, TeamRole.MEMBER);

        assertEquals(user, ut.getUser());
        assertEquals(team, ut.getTeam());
        assertEquals(TeamRole.MEMBER, ut.getRole());
    }

    @Test
    @DisplayName("동일한 역할로 변경 시에도 정상적으로 유지된다")
    void changeRole_same_role_no_problem() {

        UserTeam ut = UserTeam.create(
                TestFixtures.user(),
                TestFixtures.team(),
                TeamRole.MEMBER
        );

        ut.changeRole(TeamRole.MEMBER);

        assertEquals(TeamRole.MEMBER, ut.getRole());
    }

    @Test
    @DisplayName("detach 호출 시 user와 team 연결이 제거된다")
    void detach_removes_association() {

        UserTeam ut = UserTeam.create(
                TestFixtures.user(),
                TestFixtures.team(),
                TeamRole.MEMBER
        );

        ut.detach();

        assertNull(ut.getUser());
        assertNull(ut.getTeam());
    }

    @Test
    @DisplayName("OWNER 역할 변경 정책 테스트")
    void changeRole_owner_policy_test() {

        UserTeam ut = UserTeam.create(
                TestFixtures.user(),
                TestFixtures.team(),
                TeamRole.OWNER
        );

        assertThatThrownBy(() -> ut.changeRole(TeamRole.MEMBER))
                .isInstanceOfSatisfying(BaseException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(BaseResponseStatus.TEAM_ROLE_CHANGE_DENIED);
                    assertThat(ex.getMessage()).contains("오너는 역할을 변경할 수 없습니다.");
                });

    }
}
