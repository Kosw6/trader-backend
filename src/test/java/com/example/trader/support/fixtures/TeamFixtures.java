package com.example.trader.support.fixtures;

import com.example.trader.entity.Team;
import com.example.trader.entity.TeamRole;
import com.example.trader.entity.User;
import com.example.trader.repository.TeamRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class TeamFixtures {

    private final TeamRepository teamRepository;

    public TeamFixtures(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    /** 팀만 저장 (owner 없는 상태가 필요한 테스트에서만 사용) */
    public Team saveTeamOnly() {
        return teamRepository.saveAndFlush(TestFixtures.team());
    }

    /** 정상 상태: 팀 + owner(유니크) */
    public Team saveTeamWithOwner(User owner) {
        Team team = saveTeamOnly();
        team.addMember(owner, TeamRole.OWNER);
        return teamRepository.saveAndFlush(team);
    }

    /** 기존 팀에 MEMBER 추가 */
    public Team addMember(Team team, User member) {
        team.addMember(member, TeamRole.MEMBER);
        return teamRepository.saveAndFlush(team);
    }

    /** 팀 + owner + 멤버들 */
    public Team saveTeamWithUsers(User owner, User... members) {
        Team team = saveTeamWithOwner(owner);
        for (User m : members) {
            addMember(team, m);
        }
        return team;
    }
}
